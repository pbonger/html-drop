const readline = require('readline');
const { execSync } = require('child_process');

const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
rl.question('Page UUID: ', uuid => {
  rl.close();
  uuid = uuid.trim();
  const base = 'wrangler kv key get --binding PAGES --preview false --remote --config worker/wrangler.toml';
  try {
    const raw = execSync(`${base} "page:${uuid}"`, { encoding: 'utf8' });
    const { html: _, ...meta } = JSON.parse(raw);
    console.log(JSON.stringify(meta, null, 2));
  } catch (e) {
    console.error('Page not found or error fetching record.');
  }
});
