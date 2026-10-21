import {news,UpstreamError} from '../lib/data.js';import {route} from '../lib/http.js';
export default route(async req=>{const symbols=String(req.query?.symbols||'').toUpperCase();if(symbols&&!/^[A-Z0-9.,\-]{1,80}$/.test(symbols))throw new UpstreamError('Bad symbols',400);return{articles:await news({symbols,limit:req.query?.limit})}},{ttl:600});
