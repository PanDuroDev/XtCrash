import { readdir, readFile, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const docsRoot = path.join(root, 'src', 'content', 'docs');
const publicRoot = path.join(root, 'public');
const basePath = '/XtCrash';

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const found = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) found.push(...await walk(full));
    else if (/\.(md|mdx)$/.test(entry.name)) found.push(full);
  }
  return found;
}

function routeFor(file) {
  return path.relative(docsRoot, file).replace(/\.(md|mdx)$/, '').split(path.sep).join('/');
}

const files = await walk(docsRoot);
const routes = new Set(files.map(routeFor));
const errors = [];

for (const file of files) {
  const route = routeFor(file);
  let source = await readFile(file, 'utf8');
  source = source.replace(/^\s*```[\s\S]*?^\s*```\s*$/gm, '');
  const links = source.matchAll(/\]\(([^)]+)\)/g);
  for (const match of links) {
    let target = match[1].trim().replace(/^<|>$/g, '');
    target = target.split(/\s+/)[0];
    if (!target || /^(?:https?:|mailto:|tel:|data:|#|\/\/)/i.test(target)) continue;

    let resolved;
    try {
      resolved = new URL(target, 'https://docs.invalid/' + route + '/');
    } catch {
      errors.push(route + ': invalid link ' + target);
      continue;
    }

    let pathname = decodeURIComponent(resolved.pathname);
    if (pathname.startsWith(basePath + '/api/')) {
      const generated = path.join(publicRoot, pathname.slice(basePath.length + 1));
      try { await access(generated); }
      catch { errors.push(route + ': generated API target missing: ' + pathname); }
      continue;
    }
    if (pathname.startsWith(basePath + '/')) pathname = pathname.slice(basePath.length);
    const normalized = pathname.replace(/^\/+|\/+$/g, '');
    if (normalized === '') continue;
    if (!routes.has(normalized)) errors.push(route + ': documentation route missing: ' + target + ' -> ' + normalized);
  }
}

if (errors.length) {
  console.error('Internal documentation link check failed:');
  for (const error of errors) console.error(' - ' + error);
  process.exitCode = 1;
} else {
  console.log('Checked internal Markdown links in ' + files.length + ' documentation pages.');
}
