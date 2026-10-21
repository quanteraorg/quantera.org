import {getQuote,getHistory,overview,search,news,UpstreamError,SYM} from '../lib/data.js';import {route} from '../lib/http.js';
const SYSTEM=`You are Quantera AI inside a finance education and research app. Explain in plain English for beginners. Use the tools for every price, figure or headline and never invent data; if a tool fails or finds nothing, say so. Prices are end-of-day or delayed. Keep answers under 180 words. When analysing, use three labelled parts: **Data** (what the tools returned), **Analysis** (what it shows), **Interpretation** (what it might mean, noting this is not investment advice). End with helpful links using exactly these tokens: [[asset:SYMBOL]] or [[lesson:ID]] with ids ratios, cashflow, peg, capm, rsi, options101, bs, sharpe, kelly, var, wacc, duration, compound, pe, fees, divers, bond, dcf.`;
const TOOLS=[
 {name:'get_quote',description:'Latest end-of-day quote for a ticker.',input_schema:{type:'object',properties:{symbol:{type:'string'}},required:['symbol']}},
 {name:'get_price_stats',description:'Return, high, low and worst drop for a ticker over N trading days (max 1000).',input_schema:{type:'object',properties:{symbol:{type:'string'},days:{type:'number'}},required:['symbol']}},
 {name:'get_company',description:'Company profile and key fundamentals for a ticker.',input_schema:{type:'object',properties:{symbol:{type:'string'}},required:['symbol']}},
 {name:'search_symbols',description:'Find tickers by company name or keyword.',input_schema:{type:'object',properties:{query:{type:'string'}},required:['query']}},
 {name:'get_news',description:'Recent headlines, optionally for tickers (comma-separated).',input_schema:{type:'object',properties:{symbols:{type:'string'}}}}];
const sym=x=>{const s=String(x||'').toUpperCase();if(!SYM.test(s))throw new UpstreamError('Bad symbol',400);return s};
export const runTool=async(name,i,used)=>{try{
  if(name==='get_quote'){const s=sym(i.symbol);used.push('Quote '+s);return await getQuote(s)}
  if(name==='get_price_stats'){const s=sym(i.symbol);used.push('Price history '+s);const b=(await getHistory(s,true)).bars.slice(-Math.min(+i.days||252,1000)),c=b.map(x=>x.c);let pk=0,dd=0;c.forEach(v=>{pk=Math.max(pk,v);dd=Math.min(dd,v/pk-1)});return{symbol:s,days:c.length,returnPct:+((c.at(-1)/c[0]-1)*100).toFixed(1),high:Math.max(...b.map(x=>x.h)),low:Math.min(...b.map(x=>x.l)),worstDropPct:+(dd*100).toFixed(1)}}
  if(name==='get_company'){const s=sym(i.symbol);used.push('Company '+s);return await overview(s)}
  if(name==='search_symbols'){used.push('Search “'+String(i.query).slice(0,40)+'”');return await search(String(i.query).slice(0,40))}
  if(name==='get_news'){used.push('News');return await news({symbols:String(i.symbols||'').toUpperCase().replace(/[^A-Z0-9.,\-]/g,'').slice(0,80),limit:5})}
  return{error:'Unknown tool'}}catch(e){return{error:e.message}}};
export default route(async req=>{
  if(req.method!=='POST')throw new UpstreamError('Use POST',405);const key=process.env.ANTHROPIC_API_KEY;if(!key)throw new UpstreamError('The AI is not configured',503);
  const m=req.body?.messages;if(!Array.isArray(m)||!m.length||m.length>20||m[m.length-1].role!=='user')throw new UpstreamError('Invalid messages',400);
  const msgs=m.map(x=>{if(!['user','assistant'].includes(x.role)||typeof x.content!=='string'||!x.content.trim()||x.content.length>2000)throw new UpstreamError('Invalid messages',400);return{role:x.role,content:x.content}});
  const used=[];let text='';
  for(let turn=0;turn<5;turn++){
    const r=await fetch('https://api.anthropic.com/v1/messages',{method:'POST',headers:{'x-api-key':key,'anthropic-version':'2023-06-01','content-type':'application/json'},body:JSON.stringify({model:process.env.ANTHROPIC_MODEL||'claude-sonnet-4-6',max_tokens:900,system:SYSTEM,tools:TOOLS,messages:msgs}),signal:AbortSignal.timeout(45000)});
    if(r.status===429)throw new UpstreamError('The AI is busy. Please try again shortly.',429);if(!r.ok)throw new UpstreamError('AI provider error');
    const j=await r.json();text=(j.content||[]).filter(b=>b.type==='text').map(b=>b.text).join('\n');
    if(j.stop_reason!=='tool_use')break;
    msgs.push({role:'assistant',content:j.content});
    const results=[];for(const b of j.content.filter(b=>b.type==='tool_use'))results.push({type:'tool_result',tool_use_id:b.id,content:JSON.stringify(await runTool(b.name,b.input||{},used)).slice(0,6000)});
    msgs.push({role:'user',content:results})}
  return{text:text||'Sorry, I could not produce an answer. Please try again.',used:[...new Set(used)]}},{max:12});
