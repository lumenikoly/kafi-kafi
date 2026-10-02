import {readFileSync} from 'node:fs';
const version=process.argv[2]?.replace(/^v/,'');
const manifest=JSON.parse(readFileSync('package.json','utf8'));
const config=JSON.parse(readFileSync('src-tauri/tauri.conf.json','utf8'));
const cargo=readFileSync('src-tauri/Cargo.toml','utf8').match(/^version = "([^"]+)"/m)?.[1];
if(!version||[manifest.version,config.version,cargo].some(v=>v!==version))throw new Error('Release tag must match frontend, Rust and Tauri versions.');
