import {UpstreamError,SYM} from './data.js';
const hits=new Map(); // best-effort per-instance limiter; use Upstash/Redis if you need strict global limits
export const limited=(ip,k,max)=>{const key=ip+k,now=Date.now(),h=(hits.get(key)||[]).filter(t=>now-t<60000);h.push(now);hits.set(key,h);if(hits.size>5000)hits.clear();return h.length>max};
export const ipOf=req=>String(req.headers?.['x-forwarded-for']||req.socket?.remoteAddress||'?').split(',')[0].trim();
export const symbolParam=req=>{const s=String(req.query?.symbol||'').trim().toUpperCase();if(!SYM.test(s))throw new UpstreamError('Bad symbol',400);return s};
// wraps a handler: rate limit, JSON errors that never leak internals or keys, CDN caching
export const route=(fn,{max=60,ttl=0}={})=>async(req,res)=>{
  try{if(req.method!=='GET'&&req.method!=='POST')return res.status(405).json({error:'Method not allowed'});
    if(limited(ipOf(req),(req.url||'').split('?')[0],max))return res.status(429).json({error:'Too many requests'});
    const out=await fn(req);if(ttl)res.setHeader('Cache-Control',`public, s-maxage=${ttl}, stale-while-revalidate=${ttl*4}`);else res.setHeader('Cache-Control','no-store');
    return res.status(200).json(out)}
  catch(e){const code=e instanceof UpstreamError?e.code:500;if(code===500)console.error(e);return res.status(code).json({error:code===500?'Server error':e.message})}};
