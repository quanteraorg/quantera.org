// Personal preferences, kept small on purpose. Saved in this browser (localStorage key q_pref) and, for signed-in
// visitors, synced to their account like lesson progress. Nothing here is sent anywhere else.
export type Level = 'Beginner' | 'Intermediate' | 'Advanced';
export type Depth = 'overview' | 'detail' | 'method';
export interface Prefs { level?: Level; topics?: string[]; industries?: string[]; depth?: Depth }
export const PREFKEY = 'q_pref';
export const prefs = (): Prefs => { const p = LS<Prefs | null>(PREFKEY, null); return p && typeof p === 'object' ? p : {}; };
export const setPrefs = (p: Prefs) => SS(PREFKEY, p);
