// Runs the test harness under Node's WASI: node run.mjs <harness.wasm> <dir> <request> <creds> [wasm version]
import { readFile } from 'node:fs/promises';
import { WASI } from 'node:wasi';

const [wasmPath, dir, request, creds, version] = process.argv.slice(2);
const wasi = new WASI({
  version: 'preview1',
  preopens: { '/w': dir },
  env: { REQUEST: '/w/' + request, CREDS: '/w/' + creds, WASM_VERSION: version ?? '2' },
});
const mod = await WebAssembly.compile(await readFile(wasmPath));
const inst = await WebAssembly.instantiate(mod, wasi.getImportObject());
wasi.start(inst);
