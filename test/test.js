// Runs the API routes against FAKE Alpha Vantage / Marketaux / Anthropic responses. No keys or network needed.
process.env.ALPHAVANTAGE_API_KEY='av-test';process.env.MARKETAUX_API_TOKEN='mx-test';process.env.ANTHROPIC_API_KEY='an-test';
const {clearCache}=await import('../lib/data.js');
let pass=0,fail=0;const t=(n,c)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+n)};
const calls=[];let mode='ok',aiStep=0;
const J=(b,s=200)=>({ok:s<400,status:s,json:async()=>b});
globalThis.fetch=async(u)=>{u=String(u.href||u);calls.push(u);const q=new URL(u).searchParams;
 if(u.includes('alphavantage')){if(mode=='limit')return J({Information:'rate limit'});const f=q.get('function');
  if(f=='GLOBAL_QUOTE')return q.get('symbol')=='ZZZ'?J({'Global Quote':{}}):J({'Global Quote':{'01. symbol':'AAPL','05. price':'226.10','09. change':'1.5','10. change percent':'0.67%','06. volume':'5000','07. latest trading day':'2026-10-05','08. previous close':'224.6'}});
  if(f=='TIME_SERIES_DAILY')return J({'Time Series (Daily)':{'2026-10-02':{'1. open':'10','2. high':'12','3. low':'9','4. close':'11','5. volume':'100'},'2026-10-01':{'1. open':'9','2. high':'10','3. low':'8','4. close':'10','5. volume':'90'}}});
  if(f=='SYMBOL_SEARCH')return J({bestMatches:[{'1. symbol':'SHOP','2. name':'Shopify','3. type':'Equity','4. region':'United States','8. currency':'USD'}]});
  if(f=='OVERVIEW')return J({Symbol:'AAPL',Name:'Apple',AssetType:'Common Stock',Sector:'TECHNOLOGY',MarketCapitalization:'3400000000000',PERatio:'33',Beta:'1.1',DividendYield:'None'})}
 if(u.includes('marketaux'))return J({data:[{uuid:'1',title:'T',snippet:'S',url:'https://x.test',source:'Src',published_at:'2026-10-05T00:00:00Z',entities:[{symbol:'AAPL',name:'Apple',sentiment_score:.3},{symbol:null,name:'x'}]}]});
 if(u.includes('anthropic')){aiStep++;return aiStep%2?J({stop_reason:'tool_use',content:[{type:'tool_use',id:'tu1',name:'get_quote',input:{symbol:'AAPL'}}]}):J({stop_reason:'end_turn',content:[{type:'text',text:'**Data** Apple is $226 [[asset:AAPL]]'}]})}
 return J({},404)};
const call=async(path,q={},method='GET',body)=>{const [m,p]=[method,path];const h=(await import(p+'?v='+Math.random())).default;let code,out;const res={setHeader(){},status(c){code=c;return this},json(o){out=o;return this}};await h({method:m,query:q,body,url:path,headers:{'x-forwarded-for':'9.9.9.'+Math.floor(Math.random()*250)}},res);return{code,out}};
const P=x=>'../api/'+x+'.js';
let r=await call(P('quote'),{symbol:'aapl'});t('quote normalised',r.code==200&&r.out.price==226.1&&r.out.changePct==.67&&r.out.source=='Alpha Vantage');
const n1=calls.length;await call(P('quote'),{symbol:'aapl'});t('quote cached (no second upstream call)',calls.length==n1);
t('API key sent upstream only, never returned',!JSON.stringify(r.out).includes('av-test')&&calls[0].includes('apikey=av-test'));
r=await call(P('quote'),{symbol:'ZZZ'});t('unknown symbol -> 404',r.code==404);
r=await call(P('quote'),{symbol:'<script>'});t('bad symbol -> 400',r.code==400);
r=await call(P('history'),{symbol:'AAPL',full:'1'});t('history sorted oldest first',r.code==200&&r.out.bars[0].t=='2026-10-01'&&r.out.bars[1].c==11);
r=await call(P('search'),{q:'shop'});t('search normalised',r.out.results[0].symbol=='SHOP');
r=await call(P('search'),{q:'x'.repeat(50)});t('search long query -> 400',r.code==400);
r=await call(P('overview'),{symbol:'AAPL'});t('overview nulls "None"',r.out.dividendYield===null&&r.out.marketCap==3.4e12);
r=await call(P('news'),{symbols:'aapl'});t('news normalised, entities without symbol dropped',r.out.articles[0].symbols.length==1&&r.out.articles[0].symbols[0].sentiment==.3);
r=await call(P('news'),{symbols:'a;b'});t('bad news symbols -> 400',r.code==400);
clearCache();mode='limit';r=await call(P('quote'),{symbol:'MSFT'});t('provider rate limit -> 429 with friendly message',r.code==429&&/rate limit/i.test(r.out.error));mode='ok';
r=await call(P('health'));t('health reports configured services',r.out.market&&r.out.news&&r.out.ai);
aiStep=0;r=await call(P('ai'),{},'POST',{messages:[{role:'user',content:'Tell me about Apple'}]});t('AI runs tool loop and returns text + tools used',r.code==200&&r.out.text.includes('Apple')&&r.out.used.includes('Quote AAPL'));
r=await call(P('ai'),{},'POST',{messages:[]});t('AI rejects empty messages',r.code==400);
r=await call(P('ai'),{},'POST',{messages:[{role:'user',content:'x'.repeat(3000)}]});t('AI rejects oversize message',r.code==400);
r=await call(P('ai'),{},'POST',{messages:[{role:'system',content:'hack'},{role:'user',content:'hi'}]});t('AI rejects system role injection',r.code==400);
delete process.env.ANTHROPIC_API_KEY;r=await call(P('ai'),{},'POST',{messages:[{role:'user',content:'hi'}]});t('AI without key -> 503',r.code==503);
const hh=(await import('../api/quote.js?rl')).default;let last;for(let i=0;i<70;i++){await hh({method:'GET',query:{symbol:'AAPL'},url:'/api/quote',headers:{'x-forwarded-for':'1.2.3.4'}},{setHeader(){},status(c){last=c;return this},json(){return this}})}t('per-IP rate limit kicks in',last==429);
console.log(`\n${pass} passed, ${fail} failed`);process.exit(fail?1:0);
