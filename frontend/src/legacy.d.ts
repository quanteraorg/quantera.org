// Globals defined by the legacy bundle (public/app.js). Modules may read them;
// each one listed here is a dependency to remove as more code moves into modules.
declare const QLIVE: { ok: boolean; market: boolean };
declare function qtoast(html: string): void;
declare function LS<T>(key: string, fallback: T): T;
declare function SS(key: string, value: unknown): void;
declare function esc(s: unknown): string;
declare function fmt(n: number, digits?: number): string;
declare function go(hash: string): void;
declare function track(event: string): void;
declare function jf(url: string): Promise<{ ok: boolean; b: any }>;
declare function ACT_(kind: string): void;
declare function checkBadges(): void;
declare function route(): void;
declare const SYNC: string[];
/** Lessons: [id, title, category, level, colour, colour, description, link, [question, options[], answerIndex], fact] */
declare const LES: [string, string, string, string, string, string, string, string, [string, string[], number], string][];
declare const BADGES: [string, string, string, string, (d: any) => boolean][];
declare const DONE: Set<string>;
declare const PAGES: Record<string, (arg?: string) => void>;
declare const SUBT: Record<string, [string, string][]>;
/** Route → navigation hub, and route → [title, subtitle]. */
declare const HUB: Record<string, string>;
declare const TT: Record<string, [string, string]>;
