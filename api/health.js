import {flags} from '../lib/data.js';
export default (req,res)=>{res.setHeader('Cache-Control','no-store');res.status(200).json(flags())};
