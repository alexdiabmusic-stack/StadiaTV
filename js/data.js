/* StadiaTV — mock data layer.
   In a real deployment, SPORTS/TEAMS/MATCHES/CHANNELS/PROGRAMS would be
   replaced by data fetched from a parsed M3U/Xtream playlist + an EPG/scores
   API. Everything else in the app treats this module as that data source,
   so swapping it out later doesn't require touching app.js's rendering code. */

const SPORTS = [
  { id: 'football',   name: 'Football',    mark: 'FB', hue: '#2f81f7' },
  { id: 'basketball', name: 'Basketball',  mark: 'BB', hue: '#f5883d' },
  { id: 'nfl',         name: 'NFL',         mark: 'NF', hue: '#8b5cf6' },
  { id: 'tennis',      name: 'Tennis',      mark: 'TN', hue: '#34d17a' },
  { id: 'cricket',     name: 'Cricket',     mark: 'CR', hue: '#e0b13a' },
  { id: 'baseball',    name: 'Baseball',    mark: 'BS', hue: '#e05a6a' },
  { id: 'hockey',      name: 'Ice Hockey',  mark: 'IH', hue: '#38bdf8' },
  { id: 'combat',      name: 'Combat',      mark: 'MMA', hue: '#ef4b57' },
  { id: 'f1',          name: 'F1',          mark: 'F1', hue: '#e11d48' },
  { id: 'golf',        name: 'Golf',        mark: 'GF', hue: '#22c197' },
];

const TEAMS = [
  { abbr: 'ARS', name: 'Arsenal',     league: 'Premier League', color: '#e0243a' },
  { abbr: 'MCI', name: 'Man City',    league: 'Premier League', color: '#5cb8e6' },
  { abbr: 'CHE', name: 'Chelsea',     league: 'Premier League', color: '#1a4bb0' },
  { abbr: 'LIV', name: 'Liverpool',   league: 'Premier League', color: '#c8102e' },
  { abbr: 'RMA', name: 'Real Madrid', league: 'La Liga',        color: '#1e2a5a' },
  { abbr: 'BAR', name: 'Barcelona',   league: 'La Liga',        color: '#7a2049' },
  { abbr: 'LAL', name: 'Lakers',      league: 'NBA',            color: '#6b2fb5' },
  { abbr: 'BOS', name: 'Celtics',     league: 'NBA',            color: '#1a8f4a' },
  { abbr: 'GSW', name: 'Warriors',    league: 'NBA',            color: '#1a5fb0' },
  { abbr: 'KC',  name: 'Chiefs',      league: 'NFL',            color: '#c8102e' },
  { abbr: 'DAL', name: 'Cowboys',     league: 'NFL',            color: '#1a3a6b' },
  { abbr: 'NYY', name: 'Yankees',     league: 'MLB',            color: '#14274d' },
];

const MATCHES = [
  { id: 'm1', sport: 'football', league: 'Premier League', comp: 'Matchday 22',
    home: { name: 'Arsenal', abbr: 'ARS', color: '#e0243a' }, away: { name: 'Chelsea', abbr: 'CHE', color: '#1a4bb0' },
    hs: 2, as: 1, clock: "67'", status: 'live', fav: true,
    events: [
      { min: "12'", type: 'goal', side: 'home', who: 'Saka' },
      { min: "34'", type: 'goal', side: 'away', who: 'Palmer' },
      { min: "58'", type: 'yellow', side: 'away', who: 'Caicedo' },
      { min: "61'", type: 'goal', side: 'home', who: 'Ødegaard' },
    ],
    streams: [
      { p: 'Sky Sports Main Event', q: '4K' },
      { p: 'Sky Sports Premier League', q: 'FHD' },
      { p: 'Premier Sports 1', q: 'HD' },
      { p: 'beIN Sports 1', q: 'HD' },
      { p: 'Arena Sport 1', q: 'SD' },
    ] },
  { id: 'm2', sport: 'basketball', league: 'NBA', comp: 'Regular Season',
    home: { name: 'Lakers', abbr: 'LAL', color: '#6b2fb5' }, away: { name: 'Celtics', abbr: 'BOS', color: '#1a8f4a' },
    hs: 88, as: 91, clock: 'Q3 04:12', status: 'live', fav: true,
    events: [
      { min: 'Q1', type: 'lead', side: 'away', who: 'BOS +6' },
      { min: 'Q2', type: 'lead', side: 'home', who: 'LAL +2' },
      { min: 'Q3', type: 'lead', side: 'away', who: 'BOS +3' },
    ],
    streams: [
      { p: 'ESPN 4K', q: '4K' },
      { p: 'NBA League Pass', q: 'FHD' },
      { p: 'TNT HD', q: 'HD' },
      { p: 'Sky Sports NBA', q: 'HD' },
    ] },
  { id: 'm3', sport: 'f1', league: 'Formula 1', comp: 'Bahrain GP',
    home: { name: 'Verstappen', abbr: 'VER', color: '#1a3a8f' }, away: { name: 'Leader', abbr: 'LAP', color: '#e11d48' },
    hs: 0, as: 0, clock: 'Lap 41/57', status: 'live', fav: true, single: true,
    events: [
      { min: 'L1', type: 'info', side: 'home', who: 'Lights out' },
      { min: 'L18', type: 'info', side: 'home', who: 'VER pits' },
      { min: 'L34', type: 'info', side: 'home', who: 'Safety car' },
    ],
    streams: [
      { p: 'Sky Sports F1 4K', q: '4K' },
      { p: 'F1 TV Pro', q: 'FHD' },
      { p: 'ESPN F1', q: 'HD' },
    ] },
  { id: 'm4', sport: 'football', league: 'La Liga', comp: 'Matchday 21',
    home: { name: 'Real Madrid', abbr: 'RMA', color: '#1e2a5a' }, away: { name: 'Barcelona', abbr: 'BAR', color: '#7a2049' },
    hs: 1, as: 1, clock: "52'", status: 'live', fav: false,
    events: [
      { min: "23'", type: 'goal', side: 'home', who: 'Vinícius' },
      { min: "45'", type: 'goal', side: 'away', who: 'Lewandowski' },
      { min: "50'", type: 'red', side: 'home', who: 'Rüdiger' },
    ],
    streams: [
      { p: 'LaLiga TV 4K', q: '4K' },
      { p: 'ESPN Deportes', q: 'FHD' },
      { p: 'beIN Sports 2', q: 'HD' },
    ] },
  { id: 'm5', sport: 'nfl', league: 'NFL', comp: 'Divisional Round',
    home: { name: 'Chiefs', abbr: 'KC', color: '#c8102e' }, away: { name: 'Cowboys', abbr: 'DAL', color: '#1a3a6b' },
    hs: 21, as: 17, clock: 'Q4 07:33', status: 'live', fav: false,
    events: [
      { min: 'Q1', type: 'goal', side: 'home', who: 'TD Kelce' },
      { min: 'Q2', type: 'goal', side: 'away', who: 'TD Lamb' },
      { min: 'Q3', type: 'goal', side: 'away', who: 'FG' },
    ],
    streams: [
      { p: 'NFL RedZone', q: '4K' },
      { p: 'FOX Sports 4K', q: 'FHD' },
      { p: 'Sky Sports NFL', q: 'HD' },
    ] },
  { id: 'm6', sport: 'tennis', league: 'ATP', comp: 'Australian Open · SF',
    home: { name: 'Alcaraz', abbr: 'ALC', color: '#e0561e' }, away: { name: 'Sinner', abbr: 'SIN', color: '#2f81f7' },
    hs: 2, as: 1, clock: 'Set 4', status: 'live', fav: false,
    events: [
      { min: 'S1', type: 'info', side: 'home', who: '6-4' },
      { min: 'S2', type: 'info', side: 'away', who: '7-5' },
      { min: 'S3', type: 'info', side: 'home', who: '6-3' },
    ],
    streams: [
      { p: 'Eurosport 1 4K', q: '4K' },
      { p: 'Tennis Channel', q: 'FHD' },
      { p: 'Sky Sports Tennis', q: 'HD' },
    ] },
  { id: 'm7', sport: 'hockey', league: 'NHL', comp: 'Regular Season',
    home: { name: 'Rangers', abbr: 'NYR', color: '#1a5fb0' }, away: { name: 'Bruins', abbr: 'BOS', color: '#c9a227' },
    hs: 3, as: 2, clock: 'P3 11:40', status: 'live', fav: false,
    events: [
      { min: 'P1', type: 'goal', side: 'home', who: 'Panarin' },
      { min: 'P2', type: 'goal', side: 'away', who: 'Pastrňák' },
    ],
    streams: [
      { p: 'ESPN+ 4K', q: '4K' },
      { p: 'NHL Center Ice', q: 'HD' },
      { p: 'TSN 1', q: 'HD' },
    ] },

  { id: 'u1', sport: 'football', league: 'Premier League', comp: 'Matchday 22',
    home: { name: 'Man City', abbr: 'MCI', color: '#5cb8e6' }, away: { name: 'Liverpool', abbr: 'LIV', color: '#c8102e' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: '21:00', starts: 'in 1h 45m', fav: true,
    events: [], streams: [{ p: 'Sky Sports Main Event', q: '4K' }, { p: 'Sky Sports PL', q: 'FHD' }, { p: 'Peacock', q: 'HD' }] },
  { id: 'u2', sport: 'basketball', league: 'NBA', comp: 'Regular Season',
    home: { name: 'Warriors', abbr: 'GSW', color: '#1a5fb0' }, away: { name: 'Nuggets', abbr: 'DEN', color: '#e0b13a' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: '22:30', starts: 'in 3h 15m', fav: false,
    events: [], streams: [{ p: 'NBA League Pass', q: 'FHD' }, { p: 'ESPN', q: 'HD' }] },
  { id: 'u3', sport: 'football', league: 'Serie A', comp: 'Matchday 23',
    home: { name: 'Inter', abbr: 'INT', color: '#1a4bb0' }, away: { name: 'Juventus', abbr: 'JUV', color: '#111318' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: '20:45', starts: 'in 1h 30m', fav: false,
    events: [], streams: [{ p: 'DAZN 4K', q: '4K' }, { p: 'beIN Sports 3', q: 'HD' }] },
  { id: 'u4', sport: 'nfl', league: 'NFL', comp: 'Divisional Round',
    home: { name: 'Bills', abbr: 'BUF', color: '#1a3a8f' }, away: { name: 'Ravens', abbr: 'BAL', color: '#6b2fb5' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: 'Tomorrow 18:00', starts: 'Tomorrow', fav: false,
    events: [], streams: [{ p: 'CBS Sports 4K', q: '4K' }, { p: 'Sky Sports NFL', q: 'HD' }] },
  { id: 'u5', sport: 'combat', league: 'UFC', comp: 'UFC 312 · Main Card',
    home: { name: 'Adesanya', abbr: 'ADE', color: '#111318' }, away: { name: 'Du Plessis', abbr: 'DDP', color: '#e0b13a' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: 'Sat 03:00', starts: 'Saturday', fav: false,
    events: [], streams: [{ p: 'ESPN+ PPV 4K', q: '4K' }, { p: 'BT Sport Box Office', q: 'FHD' }] },
  { id: 'u6', sport: 'cricket', league: 'ICC', comp: 'T20 World Cup',
    home: { name: 'India', abbr: 'IND', color: '#1a5fb0' }, away: { name: 'Australia', abbr: 'AUS', color: '#e0b13a' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: 'Tomorrow 09:30', starts: 'Tomorrow', fav: false,
    events: [], streams: [{ p: 'Star Sports 4K', q: '4K' }, { p: 'Willow HD', q: 'HD' }] },
  { id: 'u7', sport: 'tennis', league: 'WTA', comp: 'Australian Open · SF',
    home: { name: 'Świątek', abbr: 'SWI', color: '#e0561e' }, away: { name: 'Sabalenka', abbr: 'SAB', color: '#e0b13a' },
    hs: 0, as: 0, clock: '', status: 'upcoming', time: '23:00', starts: 'in 3h 45m', fav: false,
    events: [], streams: [{ p: 'Eurosport 1', q: 'FHD' }, { p: 'Tennis Channel', q: 'HD' }] },
];

const CHANNELS = [
  { num: 401, name: 'Sky Sports Main Event', cat: 'Sports', color: '#2f81f7' },
  { num: 402, name: 'Sky Sports Premier League', cat: 'Football', color: '#7a2049' },
  { num: 403, name: 'TNT Sports 1', cat: 'Football', color: '#e0b13a' },
  { num: 410, name: 'ESPN', cat: 'Basketball', color: '#e0243a' },
  { num: 411, name: 'NBA TV', cat: 'Basketball', color: '#1a5fb0' },
  { num: 420, name: 'Sky Sports F1', cat: 'Motorsport', color: '#e11d48' },
  { num: 430, name: 'Eurosport 1', cat: 'Tennis', color: '#22c197' },
  { num: 440, name: 'NFL Network', cat: 'NFL', color: '#8b5cf6' },
];

const PROGRAMS = {
  401: [{ t: 'Arsenal v Chelsea', d: 120, live: true }, { t: 'Match of the Day', d: 60 }, { t: 'Football Gold', d: 60 }],
  402: [{ t: 'PL Preview', d: 60 }, { t: 'Man City v Liverpool', d: 120, soon: true }, { t: 'Goals Show', d: 60 }],
  403: [{ t: 'Serie A Live', d: 120, live: true }, { t: 'European Nights', d: 60 }, { t: 'Highlights', d: 60 }],
  410: [{ t: 'Lakers v Celtics', d: 150, live: true }, { t: 'SportsCenter', d: 30 }, { t: 'NBA Tonight', d: 60 }],
  411: [{ t: 'GameTime', d: 60 }, { t: 'Warriors v Nuggets', d: 150, soon: true }, { t: 'Hardwood', d: 30 }],
  420: [{ t: 'Bahrain GP', d: 120, live: true }, { t: 'F1 Debrief', d: 60 }, { t: 'Paddock Pass', d: 60 }],
  430: [{ t: 'Alcaraz v Sinner', d: 150, live: true }, { t: 'Game, Set, Match', d: 30 }, { t: 'ATP Weekly', d: 60 }],
  440: [{ t: 'Chiefs v Cowboys', d: 180, live: true }, { t: 'NFL GameDay', d: 60 }],
};

const TIMESLOTS = ['19:00', '19:30', '20:00', '20:30', '21:00', '21:30', '22:00'];
const EPG_CATS = ['All', 'Football', 'Basketball', 'NFL', 'Motorsport', 'Tennis'];

const PLAYLISTS = [
  { id: 'p1', type: 'Xtream', name: 'My Provider', host: 'line.provider.tv:8080', channels: '12,438', status: 'Active' },
  { id: 'p2', type: 'M3U', name: 'Backup List', host: 'iptv-backup.net/get.php', channels: '3,204', status: 'Active' },
  { id: 'p3', type: 'M3U', name: 'Sports Only', host: 'sportshd.tv/list.m3u', channels: '842', status: 'Error' },
];
