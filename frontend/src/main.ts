// Entry point for Quantera's TypeScript modules. Built by Vite into public/assets/modules.js,
// which index.html loads after the legacy bundle (public/app.js). Code moves here one feature at a time.
import { initStates } from './states';
import { initMotion } from './motion';
import { initDaily } from './daily';
import { initFolders } from './folders';
import { initGlass } from './glass';
import { initTrust } from './trust';

initStates();
initMotion();
initDaily();
initFolders();
initGlass();
initTrust();
