// Entry point for Quantera's TypeScript modules. Built by Vite into public/assets/modules.js,
// which index.html loads after the legacy bundle (public/app.js). Code moves here one feature at a time.
import { initStates } from './states';
import { initMotion } from './motion';
import { initDaily } from './daily';
import { initFolders } from './folders';
import { initGlass } from './glass';
import { initTrust } from './trust';
import { initStatements } from './statements';
import { initValuation } from './valuation';
import { initResearch } from './research';
import { initConnect } from './connect';
import { initAnalytics } from './analytics';
import { initEcon } from './econ';

initStates();
initMotion();
initDaily();
initFolders();
initGlass();
initTrust();
initStatements();
initValuation();
initResearch();
initConnect();
initAnalytics();
initEcon();
