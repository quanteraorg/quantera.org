// DATA PROVIDERS -> NORMALISATION. The browser only ever sees these normalised shapes, never vendor formats or keys.
const AV='https://www.alphavantage.co/query',MX='https://api.marketaux.com/v1';
export class UpstreamError extends Error{constructor(m,code=502){super(m);this.code=code}}
export const SYM=/^[A-Za-z0-9.\-]{1,12}$/;
const cache=new Map();
const hit=(k,ttl,fn)=>{const c=cache.get(k);if(c&&Date.now()-c.t<ttl)return Promise.resolve(c.v);return fn().then(v=>{cache.set(k,{t:Date.now(),v});if(cache.size>400)cache.delete(cache.keys().next().value);return v})};
export const clearCache=()=>cache.clear();
const av=async p=>{const key=process.env.ALPHAVANTAGE_API_KEY;if(!key)throw new UpstreamError('Market data is not configured',503);
  const u=new URL(AV);Object.entries({...p,apikey:key}).forEach(([k,v])=>u.searchParams.set(k,String(v)));
  const r=await fetch(u,{signal:AbortSignal.timeout(10000)});if(!r.ok)throw new UpstreamError('Market data provider error');
  const j=await r.json();if(j.Note||j.Information)throw new UpstreamError('Market data rate limit reached. Try again later.',429);
  if(j['Error Message'])throw new UpstreamError('Symbol not found',404);return j};
const num=x=>x!=null&&x!==''&&x!=='None'&&x!=='-'&&!Number.isNaN(+x)?+x:null;
export const getQuote=s=>hit('q'+s,60e3,async()=>{const j=(await av({function:'GLOBAL_QUOTE',symbol:s}))['Global Quote'];
  if(!j||!j['05. price'])throw new UpstreamError('Symbol not found',404);
  return{symbol:s,price:+j['05. price'],change:+j['09. change'],changePct:parseFloat(j['10. change percent']),volume:+j['06. volume'],prevClose:+j['08. previous close'],asOf:j['07. latest trading day'],source:'Alpha Vantage'}});
export const getHistory=(s,full)=>hit('h'+s+(full?1:0),6*3600e3,async()=>{const j=await av({function:'TIME_SERIES_DAILY',symbol:s,outputsize:full?'full':'compact'}),t=j['Time Series (Daily)'];
  if(!t)throw new UpstreamError('No price history for this symbol',404);
  const bars=Object.entries(t).map(([d,v])=>({t:d,o:+v['1. open'],h:+v['2. high'],l:+v['3. low'],c:+v['4. close'],v:+v['5. volume']})).sort((a,b)=>a.t<b.t?-1:1);
  return{symbol:s,bars:bars.slice(-1300),source:'Alpha Vantage',adjusted:false}});
export const search=q=>hit('s'+q.toLowerCase(),3600e3,async()=>{const j=await av({function:'SYMBOL_SEARCH',keywords:q});
  return(j.bestMatches||[]).slice(0,10).map(m=>({symbol:m['1. symbol'],name:m['2. name'],type:m['3. type'],region:m['4. region'],currency:m['8. currency']}))});
export const overview=s=>hit('o'+s,24*3600e3,async()=>{const j=await av({function:'OVERVIEW',symbol:s});if(!j.Symbol)throw new UpstreamError('No company data for this symbol',404);
  return{symbol:j.Symbol,name:j.Name,type:j.AssetType,sector:j.Sector,industry:j.Industry,country:j.Country,currency:j.Currency,exchange:j.Exchange,marketCap:num(j.MarketCapitalization),pe:num(j.PERatio),beta:num(j.Beta),dividendYield:num(j.DividendYield),high52:num(j['52WeekHigh']),low52:num(j['52WeekLow']),revenueGrowthYoY:num(j.QuarterlyRevenueGrowthYOY),profitMargin:num(j.ProfitMargin),roe:num(j.ReturnOnEquityTTM),description:j.Description}});
export const news=({symbols='',limit=6}={})=>hit('n'+symbols+limit,15*60e3,async()=>{const k=process.env.MARKETAUX_API_TOKEN;if(!k)throw new UpstreamError('News is not configured',503);
  const u=new URL(MX+'/news/all');u.searchParams.set('api_token',k);u.searchParams.set('language','en');u.searchParams.set('filter_entities','true');u.searchParams.set('limit',String(Math.min(+limit||6,10)));if(symbols)u.searchParams.set('symbols',symbols);
  const r=await fetch(u,{signal:AbortSignal.timeout(10000)});if(r.status==402||r.status==429)throw new UpstreamError('News rate limit reached. Try again later.',429);if(!r.ok)throw new UpstreamError('News provider error');
  const j=await r.json();return(j.data||[]).map(a=>({id:a.uuid,title:a.title,summary:a.snippet||a.description||'',url:a.url,source:a.source,publishedAt:a.published_at,symbols:(a.entities||[]).filter(e=>e.symbol).slice(0,5).map(e=>({symbol:e.symbol,name:e.name,sentiment:e.sentiment_score??null}))}))});
export const flags=()=>({ok:true,market:!!process.env.ALPHAVANTAGE_API_KEY,news:!!process.env.MARKETAUX_API_TOKEN,ai:!!process.env.ANTHROPIC_API_KEY});
