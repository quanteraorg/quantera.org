import {search,UpstreamError} from '../lib/data.js';import {route} from '../lib/http.js';
export default route(async req=>{const q=String(req.query?.q||'').trim();if(q.length<1||q.length>40)throw new UpstreamError('Query must be 1 to 40 characters',400);return{results:await search(q)}},{ttl:3600});
