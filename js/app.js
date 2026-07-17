/* StadiaTV — application shell.
   No framework: a single `state` object + `render()` that rebuilds the DOM
   from template strings, plus event delegation for clicks/inputs and a
   small spatial-navigation layer for D-pad/keyboard (remote-control) use. */

(function () {
  'use strict';

  const ICONS = {
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 11l8-6 8 6"/><path d="M6 10v9h12v-9"/></svg>',
    live: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><circle cx="12" cy="12" r="3.2" fill="currentColor" stroke="none"/><path d="M6.5 6.5a7.8 7.8 0 000 11M17.5 6.5a7.8 7.8 0 010 11"/></svg>',
    sports: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><circle cx="12" cy="12" r="8.2"/><path d="M12 3.8v16.4M3.8 12h16.4" stroke-linecap="round"/></svg>',
    search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.3"/><path d="M15.2 15.2L20 20"/></svg>',
    settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/></svg>',
    back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
    refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 10-2.3 6.3M20 5v5h-5"/></svg>',
    play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>',
    close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  };

  const NAV_ITEMS = [
    { id: 'home', label: 'Home', icon: ICONS.home },
    { id: 'live', label: 'Live TV', icon: ICONS.live },
    { id: 'sports', label: 'Sports', icon: ICONS.sports },
    { id: 'search', label: 'Search', icon: ICONS.search },
    { id: 'settings', label: 'Settings', icon: ICONS.settings },
  ];

  const state = {
    screen: 'onboarding',
    obStep: 0,
    obMethod: 'm3u',
    m3uUrl: '',
    xtHost: '',
    xtUser: '',
    xtPass: '',
    favSports: ['nfl', 'nba', 'mlb', 'nhl', 'college-football'],
    favTeams: ['ARS', 'LAL', 'MCI'],
    selectedSport: 'all',
    sportsTab: 'live',
    sportsDateOffset: 0,
    sportsLoading: false,
    sportsMatches: [],
    sportsTeams: [],
    sportsNews: [],
    sportsError: '',
    sportsUpdatedAt: null,
    activeMatch: 'm1',
    currentStreamIdx: 0,
    playerMode: 'match',
    activeChannel: 401,
    query: '',
    epgCat: 'All',
    prefAutoplay: true,
    prefScores: true,
    prefStartHome: false,
    syncing: false,
    toast: null,
  };

  // ---------- helpers ----------
  function esc(str) {
    return String(str == null ? '' : str).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }
  function qColor(q) { return ({ '4K': '#2f81f7', FHD: '#34d17a', HD: '#38bdf8', SD: '#8a94a3' })[q] || '#8a94a3'; }
  function qBg(q) { return ({ '4K': 'rgba(47,129,247,.16)', FHD: 'rgba(52,209,122,.16)', HD: 'rgba(56,189,248,.14)', SD: 'rgba(138,148,163,.14)' })[q] || 'rgba(138,148,163,.14)'; }
  function sportsCatalog() { return window.StadiaSportsApi ? window.StadiaSportsApi.feeds : SPORTS; }
  function sportName(id) {
    const s = sportsCatalog().find((x) => x.id === id) || SPORTS.find((x) => x.id === id);
    return s ? s.name : id;
  }
  function allMatches() { return state.sportsMatches.length ? state.sportsMatches : MATCHES; }
  function allTeams() { return state.sportsTeams.length ? state.sportsTeams : TEAMS; }
  function findMatch(id) { return allMatches().find((m) => m.id === id) || MATCHES.find((m) => m.id === id) || allMatches()[0] || MATCHES[0]; }
  function catalogueDb() { return window.StadiaIngestion ? window.StadiaIngestion.loadDb() : { sources: [], categories: [], mediaItems: [] }; }
  function categoryName(db, id) { const cat = (db.categories || []).find((c) => c.id === id); return cat ? cat.name : 'Live TV'; }
  function channelColor(index) {
    const colors = ['#2f81f7', '#7a2049', '#e0b13a', '#e0243a', '#1a5fb0', '#e11d48', '#22c197', '#8b5cf6', '#34d17a', '#38bdf8'];
    return colors[index % colors.length];
  }
  function importedChannels() {
    const db = catalogueDb();
    return (db.mediaItems || [])
      .filter((item) => (item.kind === 'live' || item.kind === 'radio') && item.status !== 'disabled')
      .map((item, index) => {
        const attrs = (item.metadata && item.metadata.m3uAttributes) || {};
        return {
          key: item.id,
          num: item.id,
          displayNum: attrs['tvg-chno'] || (item.metadata && item.metadata.num) || (900 + index),
          name: item.name,
          cat: categoryName(db, item.categoryId),
          color: channelColor(index),
          logoUrl: item.logoUrl,
          item,
        };
      });
  }
  function allChannels() {
    const imported = importedChannels();
    if (imported.length) return imported;
    return CHANNELS.map((ch) => ({ ...ch, key: String(ch.num), displayNum: ch.num }));
  }
  function channelKey(ch) { return String(ch.key || ch.num); }
  function channelBadge(ch) { return esc(ch.displayNum || ch.num || 'TV'); }
  function findChannel(num) { return allChannels().find((c) => channelKey(c) === String(num)) || allChannels()[0]; }
  function channelNow(ch) { const p = PROGRAMS[ch.num] || []; return p.find((x) => x.live) || p[0] || { t: ch.item ? 'Live channel' : 'On air' }; }
  function channelNext(ch) { const p = PROGRAMS[ch.num] || []; const now = channelNow(ch); return p[p.indexOf(now) + 1] || null; }
  function guideCategories() {
    return ['All', ...Array.from(new Set(allChannels().map((ch) => ch.cat).filter(Boolean)))];
  }
  function sourceSummaries() {
    const sources = catalogueDb().sources || [];
    if (!sources.length) return PLAYLISTS;
    return sources.map((source) => ({
      id: source.id,
      type: source.type === 'xtream' ? 'Xtream' : 'M3U',
      name: source.name,
      host: source.type === 'xtream' ? source.host : window.StadiaIngestion.redacted(source.url),
      channels: String(source.stats && source.stats.live != null ? source.stats.live : 0),
      status: source.status === 'syncing' ? 'Syncing' : source.status === 'error' ? 'Error' : 'Active',
      lastSuccessfulSyncAt: source.lastSuccessfulSyncAt,
      lastError: source.lastError,
    }));
  }
  function statusColor(status) {
    return ({ Active: '#34d17a', Syncing: '#57a2ff', Error: '#ef4b57' })[status] || '#8a94a3';
  }
  function statusBg(status) {
    return ({ Active: 'rgba(52,209,122,.14)', Syncing: 'rgba(47,129,247,.14)', Error: 'rgba(239,75,87,.14)' })[status] || 'rgba(138,148,163,.14)';
  }
  function normalizeXtreamHost(value) {
    const trimmed = String(value || '').trim().replace(/\/+$/, '');
    if (!trimmed) return '';
    return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  }
  function sourceNameFromUrl(value, fallback) {
    const trimmed = String(value || '').trim();
    if (/^#EXTM3U/i.test(trimmed)) return 'Pasted M3U playlist';
    try { return new URL(trimmed).host || fallback; } catch (err) { return fallback; }
  }
  async function connectPlaylistFromOnboarding() {
    if (!window.StadiaIngestion) {
      toast('Playlist ingestion is unavailable.');
      return;
    }
    if (state.syncing) return;

    const isXtream = state.obMethod === 'xtream';
    const config = isXtream
      ? {
          type: 'xtream',
          name: sourceNameFromUrl(normalizeXtreamHost(state.xtHost), 'Xtream provider'),
          host: normalizeXtreamHost(state.xtHost),
          username: state.xtUser.trim(),
          password: state.xtPass,
        }
      : {
          type: 'm3u',
          name: sourceNameFromUrl(state.m3uUrl, 'M3U playlist'),
          url: state.m3uUrl.trim(),
        };

    if ((isXtream && (!config.host || !config.username || !config.password)) || (!isXtream && !config.url)) {
      toast('Enter your playlist details first.');
      return;
    }

    try {
      setState({ syncing: true });
      const result = await window.StadiaIngestion.addSource(config);
      const firstChannel = importedChannels()[0];
      setState({
        syncing: false,
        obStep: 1,
        activeChannel: firstChannel ? channelKey(firstChannel) : state.activeChannel,
        epgCat: 'All',
      });
      toast(`Synced ${result.live.length} live channels.`);
    } catch (err) {
      setState({ syncing: false });
      toast(`Sync failed: ${err.message || 'provider unavailable'}`);
    }
  }
  async function resyncPlaylist(id) {
    if (!window.StadiaIngestion || /^p\d+/.test(id)) {
      toast('Mock playlist refreshed.');
      return;
    }
    try {
      setState({ syncing: true });
      const result = await window.StadiaIngestion.syncSource(id);
      setState({ syncing: false });
      toast(`Synced ${result.live.length} live channels.`);
    } catch (err) {
      setState({ syncing: false });
      toast(`Sync failed: ${err.message || 'provider unavailable'}`);
    }
  }
  function resolveChannelBeforePlayback(channel) {
    if (!channel || !channel.item || !window.StadiaIngestion) return '';
    return window.StadiaIngestion.resolvePlaybackUrl(channel.item);
  }
  function selectedSportsForApi() {
    const ids = new Set(sportsCatalog().map((feed) => feed.id));
    const selected = state.favSports.filter((id) => ids.has(id));
    return selected.length ? selected : sportsCatalog().slice(0, 5).map((feed) => feed.id);
  }
  function sportsDate() {
    return window.StadiaSportsApi ? window.StadiaSportsApi.addDays(new Date(), state.sportsDateOffset) : new Date();
  }
  async function refreshSportsData(opts) {
    if (!window.StadiaSportsApi || state.sportsLoading) return;
    const offset = opts && typeof opts.offset === 'number' ? opts.offset : state.sportsDateOffset;
    state.sportsLoading = true;
    state.sportsError = '';
    render();
    try {
      const result = await window.StadiaSportsApi.hydrate(selectedSportsForApi(), {
        date: window.StadiaSportsApi.addDays(new Date(), offset),
      });
      const favSet = new Set(state.favTeams.map((team) => String(team).toUpperCase()));
      const matches = result.matches.map((match) => ({
        ...match,
        fav: favSet.has(match.home.abbr) || favSet.has(match.away.abbr),
      }));
      setState({
        sportsLoading: false,
        sportsMatches: matches,
        sportsTeams: result.teams,
        sportsNews: result.news,
        sportsUpdatedAt: result.updatedAt,
        sportsError: result.errors.length ? result.errors[0] : '',
      });
    } catch (err) {
      setState({ sportsLoading: false, sportsError: err.message || 'Sports feeds failed' });
    }
  }

  function setState(patch) {
    Object.assign(state, patch);
    render();
  }

  function toast(msg) {
    state.toast = msg;
    render();
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { state.toast = null; render(); }, 2200);
  }

  // ---------- shared fragments ----------
  function eventMeta(type) {
    return ({
      goal: ['#34d17a', 'GOAL'], yellow: ['#e0b13a', 'YELLOW'], red: ['#ef4b57', 'RED CARD'],
      lead: ['#57a2ff', 'LEAD'], info: ['#8a94a3', 'INFO'],
    })[type] || ['#8a94a3', 'INFO'];
  }

  function scoreBadge(live, clock, timeLabel) {
    if (live) {
      return `<div class="live-pill"><span class="live-dot"></span>${esc(clock)}</div>`;
    }
    return `<div class="soon-pill">${esc(timeLabel || '')}</div>`;
  }

  function matchCard(m, opts) {
    opts = opts || {};
    const live = m.status === 'live';
    const size = opts.size || 'md'; // sm | md
    return `
      <div class="card match-card match-card--${size}" tabindex="0" data-action="openMatch" data-id="${m.id}">
        <div class="match-card__top">
          <div class="muted small">${esc(m.league)}</div>
          ${scoreBadge(live, m.clock, live ? '' : m.time)}
        </div>
        <div class="match-card__row">
          <div class="team-badge" style="background:${m.home.color}">${esc(m.home.abbr)}</div>
          <div class="match-card__name">${esc(m.home.name)}</div>
          ${live ? `<div class="score">${m.hs}</div>` : ''}
        </div>
        <div class="match-card__row">
          <div class="team-badge" style="background:${m.away.color}">${esc(m.away.abbr)}</div>
          <div class="match-card__name">${esc(m.away.name)}</div>
          ${live ? `<div class="score">${m.as}</div>` : ''}
        </div>
        <div class="match-card__foot">
          <div class="muted small">${esc(sportName(m.sport))}</div>
          <div class="link-accent small">${m.streams.length} streams →</div>
        </div>
      </div>`;
  }

  // ---------- shell ----------
  function sidebarHtml() {
    const sources = sourceSummaries();
    const active = sources.find((source) => source.status === 'Active') || sources[0] || { name: 'No playlist', channels: '0' };
    return `
      <nav class="sidebar">
        <div class="brand">
          <div class="brand__mark"><span></span></div>
          <div class="brand__word">STADIA<span class="accent">TV</span></div>
        </div>
        <div class="navlist">
          ${NAV_ITEMS.map((n) => `
            <div class="navitem ${state.screen === n.id ? 'is-active' : ''}" tabindex="0" data-action="nav" data-screen="${n.id}">
              <span class="navitem__icon">${n.icon}</span>
              <span>${n.label}</span>
            </div>`).join('')}
        </div>
        <div class="sidebar__status">
          <div class="sidebar__status-row"><span class="live-dot"></span><b>${esc(active.status || 'Ready')}</b></div>
          <div class="muted small">${esc(active.name)} · <span class="text">${esc(active.channels)}</span> channels</div>
        </div>
      </nav>`;
  }

  function shell(content) {
    return `<div class="app-shell">${sidebarHtml()}<main class="content" id="content">${content}</main></div>`;
  }

  // ---------- onboarding ----------
  function renderOnboarding() {
    const dots = [0, 1, 2].map((i) => `<div class="ob-dot ${i <= state.obStep ? 'is-on' : ''}"></div>`).join('');
    let body = '';
    if (state.obStep === 0) {
      body = `
        <div class="ob-grid">
          <div>
            <div class="eyebrow">WELCOME</div>
            <h1 class="hero-title">EVERY LIVE<br>GAME. ONE<br>PLACE.</h1>
            <p class="muted lead">Load your playlist to unlock live TV, a full sports hub with real-time scores, and every stream for every match.</p>
            <div class="stat-row">
              <div><div class="stat-num accent">12k+</div><div class="muted small">CHANNELS</div></div>
              <div class="stat-sep"></div>
              <div><div class="stat-num green">LIVE</div><div class="muted small">SCORES</div></div>
              <div class="stat-sep"></div>
              <div><div class="stat-num">10</div><div class="muted small">SPORTS</div></div>
            </div>
          </div>
          <div class="panel">
            <div class="panel__title">Add your playlist</div>
            <div class="tabbar">
              <div class="tabbar__item ${state.obMethod === 'm3u' ? 'is-active' : ''}" tabindex="0" data-action="obMethod" data-method="m3u">M3U URL</div>
              <div class="tabbar__item ${state.obMethod === 'xtream' ? 'is-active' : ''}" tabindex="0" data-action="obMethod" data-method="xtream">Xtream Codes</div>
            </div>
            ${state.obMethod === 'm3u' ? `
              <label class="field-label">PLAYLIST URL</label>
              <input class="field" data-input="m3uUrl" value="${esc(state.m3uUrl)}" placeholder="http://provider.tv/get.php?...">
              <div class="muted small hint">Paste the direct link to your .m3u or .m3u8 playlist.</div>
            ` : `
              <label class="field-label">SERVER HOST</label>
              <input class="field" data-input="xtHost" value="${esc(state.xtHost)}" placeholder="http://line.provider.tv:8080">
              <div class="field-pair">
                <div>
                  <label class="field-label">USERNAME</label>
                  <input class="field" data-input="xtUser" value="${esc(state.xtUser)}" placeholder="username">
                </div>
                <div>
                  <label class="field-label">PASSWORD</label>
                  <input class="field" type="password" data-input="xtPass" value="${esc(state.xtPass)}" placeholder="••••••••">
                </div>
              </div>
            `}
            <div class="btn btn-primary btn-block ${state.syncing ? 'is-busy' : ''}" tabindex="0" data-action="obNext">${state.syncing ? 'Syncing playlist...' : 'Connect &amp; Continue'}</div>
          </div>
        </div>`;
    } else if (state.obStep === 1) {
      body = `
        <div class="ob-center">
          <div class="ob-heading">
            <h2 class="section-title">PICK YOUR SPORTS</h2>
            <p class="muted">We'll surface live scores and matches for these first.</p>
          </div>
          <div class="pick-grid pick-grid--sports">
            ${sportsCatalog().map((sp) => {
              const on = state.favSports.includes(sp.id);
              return `
                <div class="pick-tile ${on ? 'is-on' : ''}" tabindex="0" data-action="toggleSport" data-id="${sp.id}">
                  ${on ? '<div class="pick-check">✓</div>' : ''}
                  <div class="pick-icon" style="background:${on ? sp.hue : '#1c222b'};color:${on ? '#fff' : '#8a94a3'}">${sp.mark}</div>
                  <div class="pick-name">${sp.name}</div>
                </div>`;
            }).join('')}
          </div>
          <div class="ob-actions">
            <div class="btn btn-ghost" tabindex="0" data-action="obBack">Back</div>
            <div class="btn btn-primary" tabindex="0" data-action="obNext">Continue</div>
          </div>
        </div>`;
    } else {
      body = `
        <div class="ob-center">
          <div class="ob-heading">
            <h2 class="section-title">FOLLOW YOUR TEAMS</h2>
            <p class="muted">Games for these teams get pinned to your home screen.</p>
          </div>
          <div class="pick-grid pick-grid--teams">
            ${allTeams().slice(0, 36).map((t) => {
              const on = state.favTeams.includes(t.abbr);
              return `
                <div class="pick-tile pick-tile--team ${on ? 'is-on' : ''}" tabindex="0" data-action="toggleTeam" data-abbr="${t.abbr}">
                  ${on ? '<div class="pick-check pick-check--sm">✓</div>' : ''}
                  <div class="pick-icon pick-icon--team" style="background:${t.color}">${t.abbr}</div>
                  <div class="pick-name pick-name--sm">${t.name}</div>
                  <div class="muted tiny">${t.league}</div>
                </div>`;
            }).join('')}
          </div>
          <div class="ob-actions">
            <div class="btn btn-ghost" tabindex="0" data-action="obBack">Back</div>
            <div class="btn btn-primary" tabindex="0" data-action="finishOnboarding">Enter StadiaTV</div>
          </div>
        </div>`;
    }

    return `
      <div class="onboarding">
        <div class="ob-top">
          <div class="brand">
            <div class="brand__mark"><span></span></div>
            <div class="brand__word">STADIA<span class="accent">TV</span></div>
          </div>
          <div class="ob-dots">${dots}</div>
        </div>
        <div class="ob-body">${body}</div>
      </div>`;
  }

  // ---------- home ----------
  function renderHome() {
    const matches = allMatches();
    const featured = matches.find((m) => m.status === 'live') || matches[0] || MATCHES[0];
    const favs = matches.filter((m) => m.fav).slice(0, 12);
    const upcoming = matches.filter((m) => m.status === 'upcoming').slice(0, 12);
    const channels = allChannels().slice(0, 30);
    const now = new Date();
    const dateStr = now.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
    const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

    return `
      <div class="screen-pad">
        <div class="page-head">
          <div>
            <div class="muted">Good evening</div>
            <h1 class="page-title">LIVE RIGHT NOW</h1>
          </div>
          <div class="head-chips">
            <div class="chip-static">${esc(dateStr)}</div>
            <div class="chip-static chip-static--mono">${esc(timeStr)}</div>
          </div>
        </div>

        <div class="hero" tabindex="0" data-action="openMatch" data-id="${featured.id}">
          <div class="hero__top">
            <div class="live-pill live-pill--lg"><span class="live-dot"></span>LIVE</div>
            <div class="muted">${esc(sportName(featured.sport))} · ${esc(featured.league)}</div>
          </div>
          <div class="hero__bottom">
            <div class="hero__teams">
              <div class="hero__team">
                <div class="team-badge team-badge--lg" style="background:${featured.home.color}">${featured.home.abbr}</div>
                <div><div class="hero__team-name">${esc(featured.home.name)}</div><div class="muted tiny">HOME</div></div>
              </div>
              <div class="hero__score">
                <div class="hero__score-num">${featured.hs}<span class="muted">:</span>${featured.as}</div>
                <div class="green">${esc(featured.clock)}</div>
              </div>
              <div class="hero__team">
                <div class="team-badge team-badge--lg" style="background:${featured.away.color}">${featured.away.abbr}</div>
                <div><div class="hero__team-name">${esc(featured.away.name)}</div><div class="muted tiny">AWAY</div></div>
              </div>
            </div>
            <div class="hero__cta">
              <div class="hero__stat"><div class="accent stat-num-sm">${featured.streams.length}</div><div class="muted tiny">STREAMS</div></div>
              <div class="btn btn-primary btn-watch"><span class="play-tri"></span>WATCH</div>
            </div>
          </div>
        </div>

        <div class="row-head"><div class="section-title-sm">Your teams today</div><div class="link-accent" tabindex="0" data-action="nav" data-screen="sports">All matches →</div></div>
        <div class="hscroll">${favs.map((m) => matchCard(m)).join('')}</div>

        <div class="row-head"><div class="section-title-sm">Live channels</div><div class="link-accent" tabindex="0" data-action="nav" data-screen="live">Open guide →</div></div>
        <div class="hscroll">
          ${channels.map((ch) => {
            const n = channelNow(ch); const nx = channelNext(ch);
            return `
              <div class="card chan-card" tabindex="0" data-action="watchChannel" data-num="${esc(channelKey(ch))}">
                <div class="chan-card__top">
                  <div class="chan-badge" style="background:${ch.color}">${channelBadge(ch)}</div>
                  <div class="chan-card__name">${esc(ch.name)}</div>
                </div>
                <div class="chan-card__now"><span class="dot-sm"></span>${esc(n.t)}</div>
                <div class="muted small">Next: ${esc(nx ? nx.t : '—')}</div>
              </div>`;
          }).join('')}
        </div>

        <div class="row-head"><div class="section-title-sm">Starting soon</div><div class="link-accent" tabindex="0" data-action="nav" data-screen="sports">Sports hub →</div></div>
        <div class="hscroll">
          ${upcoming.map((m) => `
            <div class="card upcoming-card" tabindex="0" data-action="openMatch" data-id="${m.id}">
              <div class="match-card__top">
                <div class="muted small">${esc(m.league)}</div>
                <div class="soon-pill soon-pill--accent">${esc(m.starts)}</div>
              </div>
              <div class="upcoming-card__row">
                <div class="team-badge" style="background:${m.home.color}">${m.home.abbr}</div>
                <div class="muted vs">vs</div>
                <div class="team-badge" style="background:${m.away.color}">${m.away.abbr}</div>
                <div class="upcoming-card__time">${esc(m.time)}</div>
              </div>
              <div>${esc(m.home.name)} <span class="muted">v</span> ${esc(m.away.name)}</div>
              <div class="muted small">${esc(sportName(m.sport))} · ${esc(m.comp)}</div>
            </div>`).join('')}
        </div>
      </div>`;
  }

  // ---------- live / EPG ----------
  function renderLive() {
    const cats = guideCategories();
    const channels = allChannels().filter((c) => state.epgCat === 'All' || c.cat === state.epgCat);
    return `
      <div class="screen-pad screen-pad--flush">
        <div class="page-head page-head--pad">
          <div>
            <div class="muted">Electronic Programme Guide</div>
            <h1 class="page-title">TV GUIDE</h1>
          </div>
          <div class="muted head-live"><span class="live-dot"></span>Now · ${new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}</div>
        </div>
        <div class="chip-row chip-row--pad">
          ${cats.map((c) => `<div class="chip ${state.epgCat === c ? 'is-active' : ''}" tabindex="0" data-action="epgCat" data-cat="${esc(c)}">${esc(c)}</div>`).join('')}
        </div>
        <div class="epg-wrap">
          <div class="epg">
            <div class="epg__row epg__row--head">
              <div class="epg__label"></div>
              ${TIMESLOTS.map((t) => `<div class="epg__slot-head">${t}</div>`).join('')}
            </div>
            ${channels.map((ch) => `
              <div class="epg__row">
                <div class="epg__label">
                  <div class="chan-badge" style="background:${ch.color}">${channelBadge(ch)}</div>
                  <div><div class="epg__label-name">${esc(ch.name)}</div><div class="muted tiny">${esc(ch.cat)}</div></div>
                </div>
                <div class="epg__progs">
                  ${(PROGRAMS[ch.num] || []).map((p) => `
                    <div class="epg__prog ${p.live ? 'is-live' : ''}" style="width:${p.d * 4}px" tabindex="0" data-action="watchChannel" data-num="${esc(channelKey(ch))}">
                      ${p.live ? '<div class="epg__live"><span class="live-dot"></span>LIVE</div>' : ''}
                      <div class="epg__prog-title">${esc(p.t)}</div>
                    </div>`).join('')}
                  ${!(PROGRAMS[ch.num] || []).length ? `
                    <div class="epg__prog is-live" style="width:420px" tabindex="0" data-action="watchChannel" data-num="${esc(channelKey(ch))}">
                      <div class="epg__live"><span class="live-dot"></span>LIVE</div>
                      <div class="epg__prog-title">${esc(channelNow(ch).t)}</div>
                    </div>` : ''}
                </div>
              </div>`).join('')}
          </div>
        </div>
      </div>`;
  }

  // ---------- sports hub ----------
  function renderSports() {
    const tab = state.sportsTab;
    const matches = allMatches();
    const liveCount = matches.filter((m) => m.status === 'live').length;
    const upCount = matches.filter((m) => m.status === 'upcoming').length;
    const base = matches.filter((m) => (tab === 'live' ? m.status === 'live' : tab === 'upcoming' ? m.status === 'upcoming' : false));
    const filtered = state.selectedSport === 'all' ? base : base.filter((m) => m.sport === state.selectedSport);
    const byLeague = {};
    filtered.forEach((m) => { (byLeague[m.league] = byLeague[m.league] || []).push(m); });
    const leagues = Object.keys(byLeague);
    const dateLabel = sportsDate().toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });

    return `
      <div class="screen-pad">
        <div class="page-head">
          <div>
            <div class="muted">${state.sportsLoading ? 'Refreshing ESPN feeds...' : state.sportsUpdatedAt ? `Updated ${new Date(state.sportsUpdatedAt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}` : 'All competitions'}</div>
            <h1 class="page-title">SPORTS HUB</h1>
          </div>
          <div class="tabbar tabbar--pill">
            <div class="tabbar__item ${tab === 'live' ? 'is-active' : ''}" tabindex="0" data-action="sportsTab" data-tab="live"><span class="dot-sm dot-sm--green"></span>Live <span class="muted">${liveCount}</span></div>
            <div class="tabbar__item ${tab === 'upcoming' ? 'is-active' : ''}" tabindex="0" data-action="sportsTab" data-tab="upcoming">Upcoming <span class="muted">${upCount}</span></div>
            <div class="tabbar__item ${tab === 'news' ? 'is-active' : ''}" tabindex="0" data-action="sportsTab" data-tab="news">News <span class="muted">${state.sportsNews.length}</span></div>
          </div>
        </div>
        <div class="sports-toolbar">
          <div class="btn btn-ghost btn-sm" tabindex="0" data-action="sportsDate" data-delta="-1">Prev</div>
          <div class="chip-static">${esc(dateLabel)}</div>
          <div class="btn btn-ghost btn-sm" tabindex="0" data-action="sportsDate" data-delta="1">Next</div>
          <div class="btn btn-primary btn-sm ${state.sportsLoading ? 'is-busy' : ''}" tabindex="0" data-action="refreshSports">${state.sportsLoading ? 'Refreshing...' : 'Refresh scores'}</div>
          ${state.sportsError ? `<div class="muted small sports-error">${esc(state.sportsError)} · showing cached/mock data where available</div>` : ''}
        </div>
        <div class="chip-row">
          <div class="chip ${state.selectedSport === 'all' ? 'is-active' : ''}" tabindex="0" data-action="selectSport" data-id="all">All Sports</div>
          ${sportsCatalog().map((sp) => `<div class="chip ${state.selectedSport === sp.id ? 'is-active' : ''}" tabindex="0" data-action="selectSport" data-id="${sp.id}">${esc(sp.name)}</div>`).join('')}
        </div>
        ${tab === 'news' ? renderSportsNews() : filtered.length === 0 ? `
          <div class="empty-state"><div class="empty-state__title">No matches here right now</div><div class="muted">Try another sport or check the ${tab === 'live' ? 'Upcoming' : 'Live'} tab.</div></div>
        ` : leagues.map((lg) => `
          <div class="league-group">
            <div class="league-head"><div class="league-bar"></div><div class="section-title-sm">${esc(lg)}</div><div class="count-pill">${byLeague[lg].length}</div></div>
            <div class="match-grid">${byLeague[lg].map((m) => matchCard(m, { size: 'lg' })).join('')}</div>
          </div>`).join('')}
      </div>`;
  }

  function renderSportsNews() {
    const news = state.selectedSport === 'all'
      ? state.sportsNews
      : state.sportsNews.filter((article) => article.sport === state.selectedSport);
    if (!news.length) {
      return `<div class="empty-state"><div class="empty-state__title">No news loaded</div><div class="muted">Refresh scores or select another sport.</div></div>`;
    }
    return `<div class="news-grid">
      ${news.map((article) => `
        <div class="card news-card" tabindex="0" data-action="openNews" data-url="${esc(article.url || '')}">
          ${article.image ? `<div class="news-card__image" style="background-image:url('${esc(article.image)}')"></div>` : ''}
          <div class="muted tiny">${esc(article.league || sportName(article.sport))}</div>
          <div class="news-card__title">${esc(article.headline)}</div>
          <div class="muted small">${esc(article.description || '')}</div>
        </div>`).join('')}
    </div>`;
  }

  // ---------- match detail ----------
  function renderMatch() {
    const m = findMatch(state.activeMatch);
    const live = m.status === 'live';
    return `
      <div class="screen-pad">
        <div class="crumb" tabindex="0" data-action="nav" data-screen="sports">${ICONS.back} ${esc(sportName(m.sport))} · ${esc(m.league)} · ${esc(m.comp)}</div>

        <div class="match-hero">
          <div class="match-hero__side">
            <div class="team-badge team-badge--xl" style="background:${m.home.color}">${m.home.abbr}</div>
            <div class="match-hero__name">${esc(m.home.name)}</div>
          </div>
          <div class="match-hero__center">
            ${live ? `
              <div class="live-pill live-pill--lg"><span class="live-dot"></span>LIVE NOW</div>
              <div class="match-hero__score">${m.hs}<span class="muted">:</span>${m.as}</div>
              <div class="green match-hero__clock">${esc(m.clock)}</div>
            ` : `
              <div class="match-hero__time">${esc(m.time)}</div>
              <div class="link-accent match-hero__starts">STARTS ${esc(m.time || '')}</div>
            `}
          </div>
          <div class="match-hero__side">
            <div class="team-badge team-badge--xl" style="background:${m.away.color}">${m.away.abbr}</div>
            <div class="match-hero__name">${esc(m.away.name)}</div>
          </div>
        </div>

        <div class="match-detail-grid">
          <div class="panel">
            <div class="panel__title">Match timeline</div>
            ${m.events.length ? `
              <div class="timeline">
                ${m.events.map((e) => {
                  const [color, label] = eventMeta(e.type);
                  const isHome = e.side === 'home';
                  return `
                    <div class="timeline__row">
                      <div class="timeline__side timeline__side--home">${isHome ? `<div class="timeline__who"><div><div class="timeline__name">${esc(e.who)}</div><div class="timeline__type" style="color:${color}">${label}</div></div><span class="dot-sm" style="background:${color}"></span></div>` : ''}</div>
                      <div class="timeline__min">${esc(e.min)}</div>
                      <div class="timeline__side timeline__side--away">${!isHome ? `<div class="timeline__who"><span class="dot-sm" style="background:${color}"></span><div><div class="timeline__name">${esc(e.who)}</div><div class="timeline__type" style="color:${color}">${label}</div></div></div>` : ''}</div>
                    </div>`;
                }).join('')}
              </div>` : `<div class="muted empty-timeline">Timeline will populate once the match kicks off.</div>`}
          </div>
          <div class="panel">
            <div class="panel__title-row"><div class="panel__title">Streams</div><div class="count-pill count-pill--accent">${m.streams.length} available</div></div>
            <div class="btn btn-primary btn-block btn-watch-best" tabindex="0" data-action="playMatch" data-id="${m.id}" data-idx="0"><span class="play-tri"></span>WATCH IN 4K</div>
            <div class="stream-list">
              ${m.streams.map((st, i) => `
                <div class="stream-row" tabindex="0" data-action="playMatch" data-id="${m.id}" data-idx="${i}">
                  <div class="stream-q" style="color:${qColor(st.q)};background:${qBg(st.q)}">${st.q}</div>
                  <div class="stream-name">${esc(st.p)}</div>
                  <div class="stream-play">${ICONS.play}</div>
                </div>`).join('')}
            </div>
          </div>
        </div>
      </div>`;
  }

  // ---------- player ----------
  function renderPlayer() {
    const pm = state.playerMode;
    const isMatch = pm === 'match';
    const backAction = isMatch
      ? 'data-action="nav" data-screen="match"'
      : 'data-action="nav" data-screen="live"';

    let title, subtitle, curName, curQ, extraHtml;
    if (isMatch) {
      const m = findMatch(state.activeMatch);
      const cur = m.streams[state.currentStreamIdx] || m.streams[0];
      title = `${m.home.name}  v  ${m.away.name}`;
      subtitle = `${sportName(m.sport)} · ${m.league}`;
      curName = cur.p; curQ = cur.q;
      extraHtml = `
        <div class="player__score">
          <div class="player__score-team"><div class="chan-badge" style="background:${m.home.color}">${m.home.abbr}</div><span class="mono-num">${m.hs}</span></div>
          <span class="muted">:</span>
          <div class="player__score-team"><span class="mono-num">${m.as}</span><div class="chan-badge" style="background:${m.away.color}">${m.away.abbr}</div></div>
          <div class="player__score-sep"></div>
          <span class="green">${esc(m.clock)}</span>
        </div>`;
    } else {
      const ch = findChannel(state.activeChannel);
      const now = channelNow(ch);
      title = ch.name;
      subtitle = `${ch.cat} · Ch ${ch.displayNum || ch.num}`;
      curName = ch.name; curQ = 'HD';
      extraHtml = '';
      state._playerNow = now.t;
    }

    const switcher = isMatch ? (() => {
      const m = findMatch(state.activeMatch);
      return `
        <div class="switcher">
          <div class="section-title-sm">Switch stream</div>
          <div class="hscroll hscroll--tight">
            ${m.streams.map((st, i) => `
              <div class="switch-chip ${i === state.currentStreamIdx ? 'is-active' : ''}" tabindex="0" data-action="selectStream" data-idx="${i}">
                <div class="stream-q" style="color:${qColor(st.q)};background:${qBg(st.q)}">${st.q}</div>
                <div>${esc(st.p)}</div>
              </div>`).join('')}
          </div>
        </div>`;
    })() : `
        <div class="switcher">
          <div class="section-title-sm">Channels</div>
          <div class="hscroll hscroll--tight">
            ${allChannels().slice(0, 80).map((ch) => `
              <div class="switch-chip switch-chip--chan ${channelKey(ch) === String(state.activeChannel) ? 'is-active' : ''}" tabindex="0" data-action="watchChannel" data-num="${esc(channelKey(ch))}">
                <div class="chan-badge" style="background:${ch.color}">${channelBadge(ch)}</div>
                <div><div>${esc(ch.name)}</div><div class="muted tiny">${esc(channelNow(ch).t)}</div></div>
              </div>`).join('')}
          </div>
        </div>`;

    return `
      <div class="player-screen">
        <div class="player">
          <div class="player__top">
            <div class="player__title-block">
              <div class="icon-btn" tabindex="0" ${backAction}>${ICONS.back}</div>
              <div>
                <div class="player__title">${esc(title)}</div>
                <div class="muted player__subtitle">${esc(subtitle)}</div>
              </div>
            </div>
            <div class="player__badges">
              <div class="live-pill live-pill--lg"><span class="live-dot"></span>LIVE</div>
              <div class="chip-static">${esc(curQ)}</div>
            </div>
          </div>
          ${extraHtml}
          <div class="player__center" tabindex="0" data-action="togglePlay">
            <div class="player__play-btn">${state._paused ? ICONS.play : '<div class="pause-icon"><span></span><span></span></div>'}</div>
          </div>
          <div class="player__bottom">
            <div class="player__scrub"><div class="player__scrub-fill"></div><div class="player__scrub-knob"></div></div>
            <div class="player__meta">
              <span class="green">${state._paused ? '❚❚ PAUSED' : '● LIVE'}</span>
              <span>Source: ${esc(curName)}</span>
              <span class="muted player__hint">Press OK to switch stream</span>
            </div>
          </div>
        </div>
        ${switcher}
      </div>`;
  }

  // ---------- search ----------
  function renderSearch() {
    const q = state.query.trim().toLowerCase();
    const hasQ = q.length > 0;
    const mHits = hasQ ? allMatches().filter((m) => `${m.home.name}${m.away.name}${m.league}${m.comp}${sportName(m.sport)}`.toLowerCase().includes(q)) : [];
    const cHits = hasQ ? allChannels().filter((c) => `${c.name}${c.cat}`.toLowerCase().includes(q)).slice(0, 50) : [];
    const tHits = hasQ ? allTeams().filter((t) => `${t.name}${t.abbr}${t.league}`.toLowerCase().includes(q)).slice(0, 50) : [];
    const empty = hasQ && !mHits.length && !cHits.length && !tHits.length;

    let resultsHtml = '';
    if (hasQ) {
      resultsHtml = `
        ${mHits.length ? `<div class="section-block"><div class="section-title-sm">Matches</div><div class="match-grid match-grid--search">${mHits.map((m) => matchCard(m, { size: 'lg' })).join('')}</div></div>` : ''}
        ${tHits.length ? `<div class="section-block"><div class="section-title-sm">Teams</div><div class="chip-row">${tHits.map((t) => `<div class="team-chip"><div class="chan-badge" style="background:${t.color}">${t.abbr}</div><div><div>${esc(t.name)}</div><div class="muted tiny">${esc(t.league)}</div></div></div>`).join('')}</div></div>` : ''}
        ${cHits.length ? `<div class="section-block"><div class="section-title-sm">Channels</div><div class="chip-row">${cHits.map((c) => `<div class="team-chip" tabindex="0" data-action="watchChannel" data-num="${esc(channelKey(c))}"><div class="chan-badge" style="background:${c.color}">${channelBadge(c)}</div><div><div>${esc(c.name)}</div><div class="muted tiny">${esc(c.cat)}</div></div></div>`).join('')}</div></div>` : ''}
        ${empty ? `<div class="empty-state"><div class="empty-state__title">No results</div><div class="muted">Try a team, league or sport name.</div></div>` : ''}
      `;
    } else {
      const popular = ['Arsenal', 'NBA', 'Formula 1', 'Premier League', 'UFC 312'];
      const trending = allMatches().filter((m) => m.status === 'live').slice(0, 4);
      resultsHtml = `
        <div class="section-title-sm">Popular searches</div>
        <div class="chip-row chip-row--wrap">${popular.map((p) => `<div class="chip chip--pill" tabindex="0" data-action="setQuery" data-q="${esc(p)}">${p}</div>`).join('')}</div>
        <div class="section-title-sm">Trending live</div>
        <div class="match-grid match-grid--search">${trending.map((m) => matchCard(m, { size: 'lg' })).join('')}</div>
      `;
    }

    return `
      <div class="screen-pad">
        <h1 class="page-title page-title--mb">SEARCH</h1>
        <div class="search-bar">
          ${ICONS.search}
          <input class="search-input" data-input="query" value="${esc(state.query)}" placeholder="Search teams, matches, channels or sports…">
        </div>
        ${resultsHtml}
      </div>`;
  }

  // ---------- settings ----------
  function renderSettings() {
    const playlists = sourceSummaries();
    return `
      <div class="screen-pad screen-pad--narrow">
        <h1 class="page-title page-title--mb">SETTINGS</h1>

        <div class="row-head"><div class="section-title-sm">Playlists</div><div class="btn btn-primary btn-sm" tabindex="0" data-action="addPlaylist"><span>+</span> Add playlist</div></div>
        <div class="playlist-list">
          ${playlists.map((p) => `
            <div class="playlist-row">
              <div class="playlist-type" style="color:${p.type === 'Xtream' ? '#57a2ff' : '#e0b13a'}">${p.type}</div>
              <div class="playlist-info"><div class="playlist-name">${esc(p.name)}</div><div class="muted small">${esc(p.host)}</div></div>
              <div class="playlist-count"><div class="stat-num-sm">${esc(p.channels)}</div><div class="muted tiny">CHANNELS</div></div>
              <div class="status-pill" style="color:${statusColor(p.status)};background:${statusBg(p.status)}"><span class="dot-sm" style="background:${statusColor(p.status)}"></span>${p.status}</div>
              <div class="icon-btn" tabindex="0" data-action="reconnectPlaylist" data-id="${p.id}">${ICONS.refresh}</div>
              ${p.lastSuccessfulSyncAt ? `<div class="muted tiny">Synced ${esc(new Date(p.lastSuccessfulSyncAt).toLocaleString())}</div>` : ''}
              ${p.lastError ? `<div class="muted tiny playlist-error">${esc(p.lastError)}</div>` : ''}
            </div>`).join('')}
        </div>

        <div class="settings-grid">
          <div class="panel">
            <div class="panel__title">Favourite sports</div>
            <div class="chip-row chip-row--wrap">
              ${sportsCatalog().map((sp) => { const on = state.favSports.includes(sp.id); return `<div class="chip ${on ? 'is-active' : ''}" tabindex="0" data-action="toggleSport" data-id="${sp.id}">${esc(sp.name)}</div>`; }).join('')}
            </div>
          </div>
          <div class="panel">
            <div class="panel__title">Favourite teams</div>
            <div class="chip-row chip-row--wrap">
              ${state.favTeams.map((ab) => {
                const t = allTeams().find((x) => x.abbr === ab || x.id === ab) || { abbr: ab, name: ab, league: '', color: '#3a434f' };
                return `<div class="team-chip team-chip--removable"><div class="chan-badge" style="background:${t.color}">${t.abbr}</div><div>${esc(t.name)}</div><div class="chip-x" tabindex="0" data-action="removeFavTeam" data-abbr="${ab}">✕</div></div>`;
              }).join('') || '<div class="muted small">No favourite teams yet.</div>'}
            </div>
            <div class="muted small">Available teams</div>
            <div class="chip-row chip-row--wrap">
              ${allTeams().slice(0, 60).map((t) => {
                const on = state.favTeams.includes(t.abbr);
                return `<div class="team-chip ${on ? 'is-active' : ''}" tabindex="0" data-action="toggleTeam" data-abbr="${esc(t.abbr)}"><div class="chan-badge" style="background:${t.color}">${esc(t.abbr)}</div><div><div>${esc(t.name)}</div><div class="muted tiny">${esc(t.league || '')}</div></div></div>`;
              }).join('')}
            </div>
          </div>
        </div>

        <div class="panel toggle-panel">
          <div class="toggle-row">
            <div><div class="toggle-title">Autoplay next stream</div><div class="muted small">Jump to a backup source if the current stream drops.</div></div>
            <div class="toggle ${state.prefAutoplay ? 'is-on' : ''}" tabindex="0" data-action="togglePref" data-key="prefAutoplay"><div class="toggle__knob"></div></div>
          </div>
          <div class="toggle-row">
            <div><div class="toggle-title">Live score overlay</div><div class="muted small">Show the scoreboard on top of the player during matches.</div></div>
            <div class="toggle ${state.prefScores ? 'is-on' : ''}" tabindex="0" data-action="togglePref" data-key="prefScores"><div class="toggle__knob"></div></div>
          </div>
          <div class="toggle-row toggle-row--last">
            <div><div class="toggle-title">Open on Sports hub</div><div class="muted small">Skip the home screen and land on live sports at launch.</div></div>
            <div class="toggle ${state.prefStartHome ? 'is-on' : ''}" tabindex="0" data-action="togglePref" data-key="prefStartHome"><div class="toggle__knob"></div></div>
          </div>
        </div>
      </div>`;
  }

  // ---------- main render ----------
  const RENDERERS = {
    home: renderHome, live: renderLive, sports: renderSports,
    match: renderMatch, player: renderPlayer, search: renderSearch, settings: renderSettings,
  };

  function render() {
    const root = document.getElementById('app');
    if (state.screen === 'onboarding') {
      root.innerHTML = renderOnboarding();
    } else {
      const fn = RENDERERS[state.screen] || renderHome;
      root.innerHTML = shell(fn());
    }
    root.innerHTML += `<div class="toast ${state.toast ? 'is-shown' : ''}">${esc(state.toast || '')}</div>`;
    syncInputFocus();
  }

  // Keep focus on the input the user is typing into across re-renders.
  let lastFocusedInputSelector = null;
  function syncInputFocus() {
    if (!lastFocusedInputSelector) return;
    const el = document.querySelector(lastFocusedInputSelector);
    if (el) {
      el.focus();
      const v = el.value; el.value = ''; el.value = v; // cursor to end
    }
  }

  // ---------- actions ----------
  const actions = {
    nav(d) { setState({ screen: d.screen }); },
    obMethod(d) { setState({ obMethod: d.method }); },
    obNext() {
      if (state.obStep === 0) {
        connectPlaylistFromOnboarding();
        return;
      }
      setState({ obStep: Math.min(2, state.obStep + 1) });
    },
    obBack() { setState({ obStep: Math.max(0, state.obStep - 1) }); },
    finishOnboarding() { setState({ screen: state.prefStartHome ? 'sports' : 'home' }); },
    toggleSport(d) {
      const has = state.favSports.includes(d.id);
      setState({ favSports: has ? state.favSports.filter((x) => x !== d.id) : [...state.favSports, d.id] });
      if (window.StadiaSportsApi) setTimeout(() => refreshSportsData(), 0);
    },
    toggleTeam(d) {
      const has = state.favTeams.includes(d.abbr);
      setState({ favTeams: has ? state.favTeams.filter((x) => x !== d.abbr) : [...state.favTeams, d.abbr] });
    },
    removeFavTeam(d) { setState({ favTeams: state.favTeams.filter((x) => x !== d.abbr) }); },
    openMatch(d) { setState({ screen: 'match', activeMatch: d.id, currentStreamIdx: 0 }); },
    playMatch(d) { setState({ screen: 'player', playerMode: 'match', activeMatch: d.id, currentStreamIdx: Number(d.idx || 0) }); },
    selectStream(d) { setState({ currentStreamIdx: Number(d.idx) }); },
    watchChannel(d) {
      const ch = findChannel(d.num);
      try {
        state._resolvedPlaybackUrl = resolveChannelBeforePlayback(ch);
      } catch (err) {
        toast(`Playback unavailable: ${err.message}`);
        return;
      }
      setState({ screen: 'player', playerMode: 'channel', activeChannel: String(d.num) });
    },
    epgCat(d) { setState({ epgCat: d.cat }); },
    sportsTab(d) { setState({ sportsTab: d.tab }); },
    selectSport(d) {
      const patch = { selectedSport: d.id };
      if (d.id !== 'all' && !state.favSports.includes(d.id)) patch.favSports = [...state.favSports, d.id];
      setState(patch);
      if (patch.favSports && window.StadiaSportsApi) setTimeout(() => refreshSportsData(), 0);
    },
    refreshSports() { refreshSportsData(); },
    sportsDate(d) {
      const next = state.sportsDateOffset + Number(d.delta || 0);
      setState({ sportsDateOffset: next });
      if (window.StadiaSportsApi) setTimeout(() => refreshSportsData({ offset: next }), 0);
    },
    openNews(d) {
      if (d.url) window.open(d.url, '_blank', 'noopener');
    },
    setQuery(d) { setState({ query: d.q }); },
    addPlaylist() { setState({ screen: 'onboarding', obStep: 0 }); },
    reconnectPlaylist(d) { resyncPlaylist(d.id); },
    togglePref(d) { setState({ [d.key]: !state[d.key] }); },
    togglePlay() { state._paused = !state._paused; render(); },
  };

  // ---------- event delegation ----------
  function onClick(e) {
    const el = e.target.closest('[data-action]');
    if (!el) return;
    const name = el.dataset.action;
    if (actions[name]) actions[name](el.dataset, e);
  }

  function onKeydown(e) {
    const el = e.target.closest && e.target.closest('[data-action]');
    if (el && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      actions[el.dataset.action] && actions[el.dataset.action](el.dataset, e);
      return;
    }
    if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
      spatialNav(e.key);
      e.preventDefault();
    } else if (e.key === 'Backspace' && document.activeElement && document.activeElement.tagName !== 'INPUT') {
      goBack();
    }
  }

  function onInput(e) {
    const el = e.target.closest('[data-input]');
    if (!el) return;
    lastFocusedInputSelector = fieldSelector(el);
    setState({ [el.dataset.input]: el.value });
  }

  function fieldSelector(el) {
    return `[data-input="${el.dataset.input}"]`;
  }

  function goBack() {
    if (state.screen === 'match') actions.nav({ screen: 'sports' });
    else if (state.screen === 'player') actions.nav({ screen: state.playerMode === 'match' ? 'match' : 'live' });
    else if (state.screen !== 'home' && state.screen !== 'onboarding') actions.nav({ screen: 'home' });
  }

  // ---------- spatial navigation (D-pad / arrow keys) ----------
  function spatialNav(key) {
    const focusables = Array.from(document.querySelectorAll('[tabindex="0"]')).filter((el) => el.offsetParent !== null);
    const current = document.activeElement;
    if (!focusables.length) return;
    if (!current || !focusables.includes(current)) { focusables[0].focus(); return; }

    const cr = current.getBoundingClientRect();
    const cx = cr.left + cr.width / 2, cy = cr.top + cr.height / 2;
    let best = null, bestScore = Infinity;

    focusables.forEach((el) => {
      if (el === current) return;
      const r = el.getBoundingClientRect();
      const ex = r.left + r.width / 2, ey = r.top + r.height / 2;
      const dx = ex - cx, dy = ey - cy;
      let primary, ortho, dirOk;
      if (key === 'ArrowRight') { primary = dx; ortho = dy; dirOk = dx > 4; }
      else if (key === 'ArrowLeft') { primary = -dx; ortho = dy; dirOk = dx < -4; }
      else if (key === 'ArrowDown') { primary = dy; ortho = dx; dirOk = dy > 4; }
      else { primary = -dy; ortho = dx; dirOk = dy < -4; }
      if (!dirOk) return;
      const score = primary + Math.abs(ortho) * 2.2;
      if (score < bestScore) { bestScore = score; best = el; }
    });

    if (best) { best.focus(); best.scrollIntoView({ block: 'nearest', inline: 'nearest' }); }
  }

  // ---------- boot ----------
  document.addEventListener('click', onClick);
  document.addEventListener('keydown', onKeydown);
  document.addEventListener('input', onInput);

  render();
  setTimeout(() => refreshSportsData(), 0);
  // Focus something sensible so arrow keys work immediately.
  window.addEventListener('load', () => {
    const first = document.querySelector('[tabindex="0"]');
    if (first) first.focus();
  });

  // Debug/e2e hook — harmless in production, lets tooling drive the app
  // without simulating real click/DOM events (e.g. `StadiaTV.actions.nav({screen:'home'})`).
  window.StadiaTV = { state, actions, render, setState };
})();
