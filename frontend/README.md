# Quantera website (Vercel)

Static site in `public/` (index.html + two videos). All `/api/...` calls are proxied to the Java server by `vercel.json`.

1. Deploy the `backend/` folder first (see backend/README.md) and copy its public address, e.g. `quantera-api.onrender.com`.
2. In `vercel.json`, replace `YOUR-BACKEND-HOST` with that address (no https://, no trailing slash inside the destination except as shown).
3. Push this folder to GitHub, import it in Vercel. No build command, no framework.
4. Visit your site, then `/api/health`. If it shows market, news and ai as true, the site switches to live data automatically.

If you previously deployed the Node version: delete its `api/`, `lib/`, `test/`, `package.json` and `.env.example`, and replace `vercel.json` and `public/index.html` with the files here.
