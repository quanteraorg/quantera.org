import {getQuote} from '../lib/data.js';import {route,symbolParam} from '../lib/http.js';
export default route(req=>getQuote(symbolParam(req)),{ttl:60});
