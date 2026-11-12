import { defineConfig } from 'vite';

// Builds src/main.ts into public/assets/modules.js (one ES module, no hashing so index.html can reference it).
// public/ itself stays a plain static folder: Vite does not copy or clear it.
export default defineConfig({
  publicDir: false,
  build: {
    outDir: 'public/assets',
    emptyOutDir: true,
    target: 'es2020',
    sourcemap: true,
    lib: { entry: 'src/main.ts', formats: ['es'], fileName: () => 'modules.js' },
  },
});
