const readline = require('readline');
const { execSync } = require('child_process');
const fs = require('fs');
const action = process.argv[2];

function normalizeIp(ip) {
  if (!ip.includes(':')) return ip; // IPv4
  const halves = ip.split('::');
  const left   = halves[0] ? halves[0].split(':') : [];
  const right  = halves[1] ? halves[1].split(':') : [];
  const fill   = 8 - left.length - right.length;
  const full   = [...left, ...Array(fill).fill('0'), ...right];
  return full.slice(0, 4).join(':');
}

const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
rl.question(`IP to ${action}: `, raw => {
  rl.close();
  const ip = normalizeIp(raw.trim());
  if (ip !== raw.trim()) console.log(`→ normalized to /64 prefix: ${ip}`);
  const base = 'wrangler kv key --binding PAGES --preview false --remote --config worker/wrangler.toml';

  if (action === 'block') {
    execSync(`${base} put "block:${ip}" 1`, { stdio: 'inherit' });

    // Remove all existing uploads from this IP
    console.log(`Scanning for uploads from ${ip}...`);
    const listJson = execSync(`wrangler kv key list --binding PAGES --preview false --remote --config worker/wrangler.toml --prefix "page:"`, { encoding: 'utf8' });
    const keys = JSON.parse(listJson);
    const toDelete = [];
    for (const k of keys) {
      try {
        const val = execSync(`${base} get "${k.name}"`, { encoding: 'utf8' });
        const record = JSON.parse(val);
        if (record.ip === ip) toDelete.push({ name: k.name });
      } catch {}
    }
    if (toDelete.length === 0) {
      console.log('No uploads found from this IP.');
    } else {
      console.log(`Deleting ${toDelete.length} upload(s) from ${ip}...`);
      const tmp = '/tmp/kv-delete-ip.json';
      fs.writeFileSync(tmp, JSON.stringify(toDelete));
      execSync(`wrangler kv bulk delete --binding PAGES --preview false --remote --config worker/wrangler.toml ${tmp}`, { stdio: 'inherit' });
    }
  } else {
    execSync(`${base} delete "block:${ip}"`, { stdio: 'inherit' });
  }
});
