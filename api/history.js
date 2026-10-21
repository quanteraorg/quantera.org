import {getHistory} from '../lib/data.js';import {route,symbolParam} from '../lib/http.js';
export default route(req=>getHistory(symbolParam(req),req.query?.full==='1'),{ttl:3600});
