/* StadiaTV — playlist ingestion layer.
   Converts M3U and Xtream-compatible catalogues into one normalized model.
   Sync is intentionally separate from playback URL resolution. */

(function () {
  'use strict';

  const DB_KEY = 'stadiatv.catalogue.v1';
  const REQUEST_TIMEOUT_MS = 18000;
  const MAX_TEXT_BYTES = 20 * 1024 * 1024;
  const MISSING_PURGE_AFTER = 3;

  const emptyDb = () => ({
    sources: [],
    categories: [],
    mediaItems: [],
    programmes: [],
    syncRuns: [],
  });

  function nowIso() {
    return new Date().toISOString();
  }

  function loadDb() {
    try {
      const parsed = JSON.parse(localStorage.getItem(DB_KEY) || 'null');
      return parsed && Array.isArray(parsed.mediaItems) ? parsed : emptyDb();
    } catch (err) {
      return emptyDb();
    }
  }

  function saveDb(db) {
    localStorage.setItem(DB_KEY, JSON.stringify(db));
  }

  function hash(value) {
    const str = String(value || '');
    let h = 2166136261;
    for (let i = 0; i < str.length; i += 1) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return (h >>> 0).toString(36);
  }

  function normalizeName(value) {
    return String(value || '').trim().replace(/\s+/g, ' ').toLowerCase();
  }

  function safeUrl(value, baseUrl) {
    try {
      return new URL(String(value || '').trim(), baseUrl || window.location.href);
    } catch (err) {
      return null;
    }
  }

  function usableBaseUrl(value) {
    try {
      return new URL(String(value || '')).href;
    } catch (err) {
      return window.location.href;
    }
  }

  function publicHost(url) {
    const parsed = safeUrl(url);
    if (!parsed) return '';
    return parsed.host.toLowerCase();
  }

  function sourceIdFor(config) {
    return `${config.type}:${hash(`${config.type}|${config.url || config.host || ''}|${config.username || ''}`)}`;
  }

  function categoryId(sourceId, name, providerId) {
    return `${sourceId}:cat:${providerId || hash(normalizeName(name))}`;
  }

  function mediaIdForM3u(sourceId, entry) {
    const attrs = entry.attributes || {};
    const epgId = attrs['tvg-id'] || attrs['tvg-name'] || '';
    if (epgId) return `${sourceId}:live:${hash(`${epgId}|${publicHost(entry.url)}`)}`;
    return `${sourceId}:live:${hash(`${normalizeName(entry.name)}|${normalizeName(attrs['group-title'])}|${entry.url}`)}`;
  }

  function mediaIdForXtream(sourceId, kind, providerItemId) {
    return `${sourceId}:${kind}:${providerItemId}`;
  }

  function parseAttributes(metadata) {
    const attributes = {};
    const quoted = /([\w-]+)="([^"]*)"/g;
    let match;
    while ((match = quoted.exec(metadata))) {
      attributes[match[1]] = match[2];
    }

    const consumed = metadata.replace(quoted, ' ');
    const loose = /([\w-]+)=([^\s",]+)/g;
    while ((match = loose.exec(consumed))) {
      if (attributes[match[1]] == null) attributes[match[1]] = match[2];
    }
    return attributes;
  }

  function parseExtinf(line) {
    const comma = line.lastIndexOf(',');
    const metadata = comma >= 0 ? line.slice(0, comma) : line;
    const name = comma >= 0 ? line.slice(comma + 1).trim() : '';
    return { name, attributes: parseAttributes(metadata) };
  }

  function* parseM3uText(text, baseUrl) {
    const normalized = String(text || '').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
    const lines = normalized.split('\n');
    const baseHref = usableBaseUrl(baseUrl);
    let current = null;
    let playlistAttrs = {};

    for (const raw of lines) {
      const line = raw.trim();
      if (!line) continue;

      if (line.startsWith('#EXTM3U')) {
        playlistAttrs = parseAttributes(line);
        continue;
      }
      if (line.startsWith('#EXTINF')) {
        current = parseExtinf(line);
        continue;
      }
      if (current && (line.startsWith('#EXTVLCOPT') || line.startsWith('#KODIPROP') || line.startsWith('#'))) {
        const split = line.indexOf(':');
        const key = split >= 0 ? line.slice(1, split) : line.slice(1);
        const value = split >= 0 ? line.slice(split + 1) : '';
        current.attributes[key] = value;
        continue;
      }
      if (line.startsWith('#')) continue;
      if (!current) continue;

      const url = safeUrl(line, baseHref);
      if (url) {
        yield {
          name: current.name || current.attributes['tvg-name'] || url.pathname.split('/').pop() || 'Untitled channel',
          url: url.href,
          attributes: { ...current.attributes },
          playlistAttrs,
        };
      }
      current = null;
    }
  }

  async function fetchText(url, options) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await fetch(url, {
        headers: (options && options.headers) || {},
        signal: controller.signal,
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const reader = response.body && response.body.getReader ? response.body.getReader() : null;
      if (!reader) return response.text();

      const decoder = new TextDecoder('utf-8');
      let total = 0;
      let text = '';
      while (true) {
        const chunk = await reader.read();
        if (chunk.done) break;
        total += chunk.value.byteLength;
        if (total > MAX_TEXT_BYTES) throw new Error('Download exceeded size limit');
        text += decoder.decode(chunk.value, { stream: true });
      }
      text += decoder.decode();
      return text;
    } finally {
      clearTimeout(timeout);
    }
  }

  async function fetchJson(url) {
    const text = await fetchText(url);
    return JSON.parse(text);
  }

  class M3uAdapter {
    constructor(source) {
      this.source = source;
      this.textCache = null;
    }

    async authenticate() {
      return {
        username: '',
        status: 'active',
        message: 'M3U playlist',
      };
    }

    async fetchText() {
      if (this.textCache != null) return this.textCache;
      const textish = (this.source.url || '').trim();
      if (/^#EXTM3U/i.test(textish)) {
        this.textCache = textish;
        return this.textCache;
      }
      if (!/^https?:\/\//i.test(textish)) throw new Error('Enter an HTTPS playlist URL or raw #EXTM3U text.');
      this.textCache = await fetchText(textish, { headers: this.source.headers || {} });
      return this.textCache;
    }

    async fetchCategories() {
      const entries = Array.from(parseM3uText(await this.fetchText(), this.source.url));
      const names = new Set(entries.map((e) => e.attributes['group-title']).filter(Boolean));
      return Array.from(names).map((name) => ({ id: categoryId(this.source.id, name), sourceId: this.source.id, name }));
    }

    async fetchLiveChannels() {
      const entries = Array.from(parseM3uText(await this.fetchText(), this.source.url));
      const seen = new Set();
      return entries.map((entry) => {
        const attrs = entry.attributes || {};
        const name = attrs['tvg-name'] || entry.name;
        const group = attrs['group-title'] || 'Uncategorized';
        const id = mediaIdForM3u(this.source.id, entry);
        const duplicateKey = `${normalizeName(name)}|${entry.url}`;
        const duplicateIndex = seen.has(duplicateKey) ? hash(`${duplicateKey}|${seen.size}`) : '';
        seen.add(duplicateKey);
        return {
          id: duplicateIndex ? `${id}:${duplicateIndex}` : id,
          sourceId: this.source.id,
          providerItemId: attrs['tvg-id'] || undefined,
          kind: attrs.radio === 'true' || attrs.radio === '1' ? 'radio' : 'live',
          name,
          categoryId: categoryId(this.source.id, group),
          streamUrl: entry.url,
          epgId: attrs['tvg-id'] || attrs['tvg-name'] || undefined,
          logoUrl: attrs['tvg-logo'] || undefined,
          metadata: { m3uAttributes: attrs },
          contentFingerprint: hash(`${name}|${group}|${entry.url}|${attrs['tvg-logo'] || ''}`),
          lastSeenAt: nowIso(),
        };
      });
    }

    async fetchMovies() { return []; }
    async fetchSeries() { return []; }
  }

  class XtreamAdapter {
    constructor(source) {
      this.source = source;
      this.base = String(source.host || '').replace(/\/+$/, '');
    }

    apiUrl(action, extra) {
      const url = new URL(`${this.base}/player_api.php`);
      url.searchParams.set('username', this.source.username || '');
      url.searchParams.set('password', this.source.password || '');
      if (action) url.searchParams.set('action', action);
      Object.entries(extra || {}).forEach(([key, value]) => url.searchParams.set(key, value));
      return url.href;
    }

    async authenticate() {
      const payload = await fetchJson(this.apiUrl());
      const userInfo = payload.user_info || {};
      const serverInfo = payload.server_info || {};
      return {
        username: userInfo.username || this.source.username || '',
        status: userInfo.status || 'unknown',
        serverName: serverInfo.server_protocol ? `${serverInfo.server_protocol}://${serverInfo.url || ''}` : this.base,
        raw: { user_info: userInfo, server_info: serverInfo },
      };
    }

    async fetchCategories() {
      const cats = await fetchJson(this.apiUrl('get_live_categories'));
      return (Array.isArray(cats) ? cats : []).map((cat) => ({
        id: categoryId(this.source.id, cat.category_name, cat.category_id),
        sourceId: this.source.id,
        providerCategoryId: String(cat.category_id || ''),
        name: cat.category_name || 'Uncategorized',
      }));
    }

    async fetchLiveChannels() {
      const streams = await fetchJson(this.apiUrl('get_live_streams'));
      return (Array.isArray(streams) ? streams : []).map((item) => {
        const streamId = String(item.stream_id || item.num || hash(item.name));
        return {
          id: mediaIdForXtream(this.source.id, 'live', streamId),
          sourceId: this.source.id,
          providerItemId: streamId,
          kind: 'live',
          name: item.name || `Channel ${streamId}`,
          categoryId: item.category_id ? categoryId(this.source.id, '', item.category_id) : undefined,
          containerExtension: item.container_extension || 'ts',
          epgId: item.epg_channel_id || undefined,
          logoUrl: item.stream_icon || undefined,
          metadata: {
            directUrl: item.direct_source || undefined,
            num: item.num,
            customSid: item.custom_sid,
            tvArchive: item.tv_archive,
          },
          contentFingerprint: hash(`${item.name}|${item.category_id}|${item.stream_icon}|${item.epg_channel_id}`),
          lastSeenAt: nowIso(),
        };
      });
    }

    async fetchMovies() { return []; }
    async fetchSeries() { return []; }
  }

  function adapterFor(source) {
    return source.type === 'xtream' ? new XtreamAdapter(source) : new M3uAdapter(source);
  }

  function upsertById(list, item) {
    const idx = list.findIndex((existing) => existing.id === item.id);
    if (idx >= 0) list[idx] = { ...list[idx], ...item };
    else list.push(item);
  }

  async function addSource(config) {
    const db = loadDb();
    const source = {
      id: sourceIdFor(config),
      type: config.type,
      name: config.name || (config.type === 'xtream' ? 'Xtream provider' : 'M3U playlist'),
      url: config.url || '',
      host: config.host || '',
      username: config.username || '',
      password: config.password || '',
      status: 'pending',
      capabilities: {},
      createdAt: nowIso(),
      lastSuccessfulSyncAt: null,
      lastError: '',
      stats: { live: 0, categories: 0 },
    };
    upsertById(db.sources, source);
    saveDb(db);
    return syncSource(source.id);
  }

  async function syncSource(sourceId) {
    const db = loadDb();
    const source = db.sources.find((item) => item.id === sourceId);
    if (!source) throw new Error('Playlist source not found');

    const run = {
      id: `sync:${Date.now()}:${hash(sourceId)}`,
      sourceId,
      startedAt: nowIso(),
      status: 'running',
      stats: { categories: 0, live: 0 },
      errors: [],
    };
    db.syncRuns.push(run);
    source.status = 'syncing';
    source.lastError = '';
    saveDb(db);

    try {
      const adapter = adapterFor(source);
      const account = await adapter.authenticate();
      const categories = await adapter.fetchCategories().catch((err) => {
        run.errors.push(`Categories: ${err.message}`);
        return [];
      });
      const live = await adapter.fetchLiveChannels();
      const seen = new Set(live.map((item) => item.id));

      categories.forEach((category) => upsertById(db.categories, category));
      live.forEach((item) => upsertById(db.mediaItems, {
        ...item,
        syncRunId: run.id,
        status: 'active',
        missingSyncs: 0,
        unavailableAt: undefined,
      }));

      db.mediaItems.forEach((item) => {
        if (item.sourceId !== sourceId || seen.has(item.id)) return;
        item.status = item.missingSyncs + 1 >= MISSING_PURGE_AFTER ? 'disabled' : 'missing';
        item.missingSyncs = (item.missingSyncs || 0) + 1;
        item.unavailableAt = item.unavailableAt || nowIso();
      });

      source.status = 'active';
      source.accountInfo = account;
      source.lastSuccessfulSyncAt = nowIso();
      source.stats = { categories: categories.length, live: live.length };
      run.status = 'success';
      run.finishedAt = nowIso();
      run.stats = { categories: categories.length, live: live.length };
      saveDb(db);
      return { source, run, categories, live };
    } catch (err) {
      source.status = 'error';
      source.lastError = err.message || 'Sync failed';
      run.status = 'error';
      run.finishedAt = nowIso();
      run.errors.push(source.lastError);
      saveDb(db);
      throw err;
    }
  }

  function liveItems() {
    return loadDb().mediaItems.filter((item) => (item.kind === 'live' || item.kind === 'radio') && item.status !== 'disabled');
  }

  function sourceFor(id) {
    return loadDb().sources.find((item) => item.id === id);
  }

  function resolvePlaybackUrl(mediaItem) {
    if (!mediaItem) throw new Error('No media item selected');
    if (mediaItem.streamUrl) return mediaItem.streamUrl;

    const source = sourceFor(mediaItem.sourceId);
    if (!source || source.type !== 'xtream') throw new Error('Playback source unavailable');
    if (mediaItem.metadata && mediaItem.metadata.directUrl) return mediaItem.metadata.directUrl;

    const host = String(source.host || '').replace(/\/+$/, '');
    const ext = mediaItem.containerExtension || 'ts';
    if (mediaItem.kind === 'live') return `${host}/live/${encodeURIComponent(source.username)}/${encodeURIComponent(source.password)}/${mediaItem.providerItemId}.${ext}`;
    if (mediaItem.kind === 'movie') return `${host}/movie/${encodeURIComponent(source.username)}/${encodeURIComponent(source.password)}/${mediaItem.providerItemId}.${ext}`;
    return `${host}/series/${encodeURIComponent(source.username)}/${encodeURIComponent(source.password)}/${mediaItem.providerItemId}.${ext}`;
  }

  function redacted(value) {
    return String(value || '').replace(/(username|password)=([^&]+)/gi, '$1=REDACTED');
  }

  window.StadiaIngestion = {
    loadDb,
    saveDb,
    addSource,
    syncSource,
    liveItems,
    resolvePlaybackUrl,
    parseM3uText,
    redacted,
  };
})();
