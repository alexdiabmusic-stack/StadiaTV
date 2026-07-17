/* StadiaTV — ESPN sports data adapter.
   Normalizes scoreboards, teams, and news into the app's existing match/team
   shapes. Uses HTTPS endpoints to avoid mixed-content issues. */

(function () {
  'use strict';

  const API_ROOT = 'https://site.api.espn.com/apis/site/v2/sports';
  const CACHE_TTL_MS = 5 * 60 * 1000;

  const FEEDS = [
    { id: 'college-football', sport: 'football', league: 'college-football', name: 'College Football', mark: 'CFB', hue: '#9f7aea', rankings: true },
    { id: 'nfl', sport: 'football', league: 'nfl', name: 'NFL', mark: 'NFL', hue: '#8b5cf6' },
    { id: 'mlb', sport: 'baseball', league: 'mlb', name: 'MLB', mark: 'MLB', hue: '#e05a6a' },
    { id: 'college-baseball', sport: 'baseball', league: 'college-baseball', name: 'College Baseball', mark: 'CBB', hue: '#f97316', news: false, teams: false },
    { id: 'nhl', sport: 'hockey', league: 'nhl', name: 'NHL', mark: 'NHL', hue: '#38bdf8' },
    { id: 'nba', sport: 'basketball', league: 'nba', name: 'NBA', mark: 'NBA', hue: '#f5883d' },
    { id: 'wnba', sport: 'basketball', league: 'wnba', name: 'WNBA', mark: 'WNBA', hue: '#22c197' },
    { id: 'womens-college-basketball', sport: 'basketball', league: 'womens-college-basketball', name: "Women's College Basketball", mark: 'WBB', hue: '#ec4899' },
    { id: 'mens-college-basketball', sport: 'basketball', league: 'mens-college-basketball', name: "Men's College Basketball", mark: 'MBB', hue: '#3b82f6' },
    { id: 'soccer-eng.1', sport: 'soccer', league: 'eng.1', name: 'Premier League', mark: 'EPL', hue: '#34d17a' },
    { id: 'soccer-usa.1', sport: 'soccer', league: 'usa.1', name: 'MLS', mark: 'MLS', hue: '#14b8a6' },
  ];

  const memory = new Map();

  function feedById(id) {
    return FEEDS.find((feed) => feed.id === id) || FEEDS[0];
  }

  function endpoint(feed, resource, params) {
    const path = feed.sport === 'soccer'
      ? `${API_ROOT}/soccer/${feed.league}/${resource}`
      : `${API_ROOT}/${feed.sport}/${feed.league}/${resource}`;
    const url = new URL(path);
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value != null && value !== '') url.searchParams.set(key, value);
    });
    return url.href;
  }

  async function getJson(url) {
    const cached = memory.get(url);
    if (cached && Date.now() - cached.at < CACHE_TTL_MS) return cached.value;
    const response = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error(`ESPN returned HTTP ${response.status}`);
    const value = await response.json();
    memory.set(url, { at: Date.now(), value });
    return value;
  }

  function yyyymmdd(date) {
    const d = date instanceof Date ? date : new Date(date);
    return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  }

  function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
  }

  function teamColor(team, fallback) {
    const value = (team && (team.color || team.alternateColor)) || fallback || '2f81f7';
    return value.startsWith('#') ? value : `#${value}`;
  }

  function normalizeTeam(team, fallbackSide, fallbackColor) {
    const abbr = team && (team.abbreviation || team.shortDisplayName || team.displayName || fallbackSide);
    return {
      id: team && (team.id || team.uid || team.slug || abbr),
      abbr: String(abbr || fallbackSide || 'TBD').slice(0, 5).toUpperCase(),
      name: team && (team.displayName || team.name || team.shortDisplayName) || 'TBD',
      color: teamColor(team, fallbackColor),
      logo: team && team.logo,
      slug: team && team.slug,
    };
  }

  function eventStatus(event) {
    const type = event.status && event.status.type;
    const state = type && type.state;
    if (state === 'in') return 'live';
    if (state === 'post' || (type && type.completed)) return 'final';
    return 'upcoming';
  }

  function eventTime(event) {
    const date = event.date ? new Date(event.date) : null;
    if (!date || Number.isNaN(date.getTime())) return '';
    return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  }

  function startsLabel(event) {
    const date = event.date ? new Date(event.date) : null;
    if (!date || Number.isNaN(date.getTime())) return '';
    const today = new Date();
    const day = date.toDateString() === today.toDateString()
      ? 'Today'
      : date.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
    return `${day} ${eventTime(event)}`;
  }

  function normalizeEvent(event, feed) {
    const comp = (event.competitions && event.competitions[0]) || {};
    const competitors = comp.competitors || [];
    const home = competitors.find((c) => c.homeAway === 'home') || competitors[0] || {};
    const away = competitors.find((c) => c.homeAway === 'away') || competitors[1] || {};
    const status = eventStatus(event);
    const clock = event.status && event.status.type
      ? (event.status.type.shortDetail || event.status.type.detail || event.status.displayClock || '')
      : '';

    return {
      id: `espn-${feed.id}-${event.id}`,
      provider: 'espn',
      providerGameId: event.id,
      sport: feed.id,
      league: feed.name,
      comp: event.season && event.season.slug ? event.season.slug : (event.shortName || event.name || ''),
      home: normalizeTeam(home.team, 'HOME', '#2f81f7'),
      away: normalizeTeam(away.team, 'AWAY', '#ef4b57'),
      hs: Number(home.score || 0),
      as: Number(away.score || 0),
      clock,
      status,
      time: eventTime(event),
      starts: startsLabel(event),
      fav: false,
      events: [],
      links: event.links || [],
      streams: [{ p: 'ESPN Gamecast', q: status === 'live' ? 'LIVE' : 'INFO' }],
    };
  }

  async function fetchScores(feedId, opts) {
    const feed = feedById(feedId);
    const date = opts && opts.date ? opts.date : new Date();
    const params = { dates: yyyymmdd(date) };
    if (feed.id === 'college-football') params.calendar = 'blacklist';
    let json = await getJson(endpoint(feed, 'scoreboard', params));
    const selected = new Date(date);
    const today = new Date();
    const isToday = selected.toDateString() === today.toDateString();
    if (isToday && !(json.events || []).length) {
      json = await getJson(endpoint(feed, 'scoreboard'));
    }
    return {
      feed,
      events: (json.events || []).map((event) => normalizeEvent(event, feed)),
      season: json.season || null,
      week: json.week || null,
    };
  }

  async function fetchNews(feedId) {
    const feed = feedById(feedId);
    if (feed.news === false) return { feed, articles: [] };
    const json = await getJson(endpoint(feed, 'news'));
    return {
      feed,
      articles: (json.articles || []).slice(0, 8).map((article) => ({
        id: String(article.id || article.dataSourceIdentifier || article.headline),
        headline: article.headline || 'Untitled story',
        description: article.description || '',
        url: article.links && article.links.web && article.links.web.href,
        image: article.images && article.images[0] && article.images[0].url,
      })),
    };
  }

  async function fetchTeams(feedId) {
    const feed = feedById(feedId);
    if (feed.teams === false) return { feed, teams: [] };
    const json = await getJson(endpoint(feed, 'teams'));
    const teams = (((json.sports || [])[0] || {}).leagues || [])[0];
    return {
      feed,
      teams: ((teams && teams.teams) || []).map((entry) => {
        const team = entry.team || entry;
        return {
          abbr: team.abbreviation || team.shortDisplayName || team.slug || team.id,
          id: `${feed.id}:${team.abbreviation || team.slug || team.id}`,
          providerId: team.id,
          sport: feed.id,
          name: team.displayName || team.name,
          league: feed.name,
          color: teamColor(team, feed.hue),
          logo: team.logos && team.logos[0] && team.logos[0].href,
          slug: team.slug,
        };
      }).filter((team) => team.name),
    };
  }

  async function fetchSummary(feedId, gameId) {
    const feed = feedById(feedId);
    return getJson(endpoint(feed, 'summary', { event: gameId }));
  }

  async function fetchRankings(feedId) {
    const feed = feedById(feedId);
    if (!feed.rankings) return null;
    return getJson(endpoint(feed, 'rankings'));
  }

  async function hydrate(feedIds, opts) {
    const ids = (feedIds && feedIds.length ? feedIds : FEEDS.map((feed) => feed.id)).filter((id) => FEEDS.some((feed) => feed.id === id));
    const settled = await Promise.allSettled(ids.map(async (id) => {
      const [scores, news, teams] = await Promise.all([
        fetchScores(id, opts),
        fetchNews(id).catch(() => ({ feed: feedById(id), articles: [] })),
        fetchTeams(id).catch(() => ({ feed: feedById(id), teams: [] })),
      ]);
      return { id, scores, news, teams };
    }));
    const ok = settled.filter((item) => item.status === 'fulfilled').map((item) => item.value);
    return {
      matches: ok.flatMap((item) => item.scores.events),
      news: ok.flatMap((item) => item.news.articles.map((article) => ({ ...article, sport: item.id, league: item.news.feed.name }))),
      teams: ok.flatMap((item) => item.teams.teams),
      errors: settled.filter((item) => item.status === 'rejected').map((item) => item.reason && item.reason.message || 'Sports feed failed'),
      updatedAt: new Date().toISOString(),
    };
  }

  window.StadiaSportsApi = {
    feeds: FEEDS,
    addDays,
    yyyymmdd,
    fetchScores,
    fetchNews,
    fetchTeams,
    fetchSummary,
    fetchRankings,
    hydrate,
  };
})();
