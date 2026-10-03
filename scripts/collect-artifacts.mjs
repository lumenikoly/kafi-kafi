import {readdirSync,readFileSync,mkdirSync,copyFileSync,writeFileSync} from 'node:fs';
import {join,basename} from 'node:path';
import {createHash} from 'node:crypto';
const [, ,source,destination]=process.argv;
if(!source||!destination)throw new Error('Specify source and destination.');
mkdirSync(destination,{recursive:true});
const checks=[];
function collect(dir){for(const entry of readdirSync(dir,{withFileTypes:true})){const path=join(dir,entry.name);if(entry.isDirectory())collect(path);else if(/\.(exe|zip|dmg|AppImage)$/.test(entry.name)){copyFileSync(path,join(destination,basename(path)));checks.push(`${createHash('sha256').update(readFileSync(path)).digest('hex')}  ${basename(path)}`);}}}
collect(source);
if(!checks.length)throw new Error('No release packages found.');
writeFileSync(join(destination,`${basename(destination)}-SHA256SUMS.txt`),`${checks.join('\n')}\n`);
