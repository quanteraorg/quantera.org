# Quantera website (Vercel)

Static site in `public/`: `index.html` (page shell, early theme script, base styles) loads `app.js` (the whole app) as a normal script, so the 600 KB of code is cached by browsers instead of re-downloaded with every page view. Two videos sit alongside. All `/api/...` calls are proxied to the Java server by `vercel.json`.

1. Deploy the `backend/` folder first (see backend/README.md) and copy its public address, e.g. `quantera-api.onrender.com`.
2. In `vercel.json`, replace `YOUR-BACKEND-HOST` with that address (no https://, no trailing slash inside the destination except as shown).
3. Push this folder to GitHub, import it in Vercel. No build command, no framework.
4. Visit your site, then `/api/health`. If it shows market, news and ai as true, the site switches to live data automatically.

If you previously deployed the Node version: delete its `api/`, `lib/`, `test/`, `package.json` and `.env.example`, and replace `vercel.json` and `public/index.html` with the files here.

## TypeScript modules (new code goes here)

New features are written in TypeScript under `src/` and built by Vite into `public/assets/modules.js`, which `index.html` loads after the legacy `app.js`. Code moves out of `app.js` one feature at a time (the first was the loading, number and freshness states in `src/states.ts`). Globals still read from `app.js` are listed in `src/legacy.d.ts`.

    cd frontend
    npm ci
    npm run typecheck
    npm run build        # writes public/assets/modules.js (not committed)

Vercel runs the same build (`vercel.json`: install `npm ci`, build `npm run build`, output `public`). To preview locally, build first, then serve `public/` with any static server.

## Checks

Every pull request runs `.github/workflows/ci.yml`: the backend test suite, the type check and build above, and `frontend/tests/smoke.mjs` (22 routes in both themes at desktop and phone size; fails on page errors, sideways overflow or WCAG 2 A/AA violations).
