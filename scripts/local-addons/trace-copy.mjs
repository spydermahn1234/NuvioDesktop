// fork:local-addons - copies a built Node server with only the node_modules files it can load.
//
//   node trace-copy.mjs <app-dir> <out-dir> <entry-glob-dir>...
//
// Every .js/.cjs/.mjs file under each <entry-glob-dir> (relative to <app-dir>) is an entry; @vercel/nft
// follows their require/import graph (native .node files, JSON and assets included) and the files
// it finds inside <app-dir> are copied to <out-dir> with the same relative path. The entry folders
// themselves are copied whole. A plain production install also carries the web front ends'
// libraries (Next.js, icon packs: several hundred MB) that the server never loads; this leaves them out.
import { nodeFileTrace } from '@vercel/nft';
import fs from 'node:fs';
import path from 'node:path';

const [appDir, outDir, ...entryDirs] = process.argv.slice(2);
if (!appDir || !outDir || entryDirs.length === 0) {
  console.error('usage: trace-copy.mjs <app-dir> <out-dir> <entry-dir>...');
  process.exit(2);
}
const base = path.resolve(appDir);
const out = path.resolve(outDir);

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules') walk(full, files);
    } else if (/\.(c|m)?js$/.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
}

const entries = entryDirs.flatMap((dir) => walk(path.join(base, dir)));
const { fileList, warnings } = await nodeFileTrace(entries, { base, processCwd: base, mixedModules: true });
let copied = 0;
for (const relative of fileList) {
  const from = path.join(base, relative);
  if (!from.startsWith(base) || !fs.existsSync(from) || fs.statSync(from).isDirectory()) continue;
  const to = path.join(out, relative);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(fs.realpathSync(from), to);
  copied++;
}
for (const dir of entryDirs) fs.cpSync(path.join(base, dir), path.join(out, dir), { recursive: true, dereference: true });
const missing = [...warnings].filter((w) => /Cannot find module/.test(String(w))).length;
console.log(`[trace-copy] ${entries.length} entries, ${copied} files copied, ${missing} unresolved requires`);
