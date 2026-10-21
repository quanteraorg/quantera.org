import {overview} from '../lib/data.js';import {route,symbolParam} from '../lib/http.js';
export default route(req=>overview(symbolParam(req)),{ttl:86400});
