const fs = require("fs");
const path = require("path");

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function copySqliteAssets() {
  const srcDir = "node_modules/@sqlite.org/sqlite-wasm/sqlite-wasm/jswasm";
  const dstDir = "public/js";
  ensureDir(dstDir);
  for (const name of ["sqlite3.wasm", "sqlite3-opfs-async-proxy.js"]) {
    fs.copyFileSync(path.join(srcDir, name), path.join(dstDir, name));
  }
}

function copyYogaShim() {
  const pkgSrc = "node_modules/yoga-layout/package.json";
  const srcRoot = "node_modules/yoga-layout/dist";
  const dstPkg = "target/shims/yoga-layout";
  const dstRoot = path.join(dstPkg, "dist");
  ensureDir(dstPkg);
  fs.copyFileSync(pkgSrc, path.join(dstPkg, "package.json"));
  fs.cpSync(srcRoot, dstRoot, { recursive: true });
  const binPath = path.join(dstRoot, "binaries/yoga-wasm-base64-esm.js");
  const raw = fs.readFileSync(binPath, "utf8");
  fs.writeFileSync(binPath, raw.replace(/import\.meta\.url/g, '""'));
}

function copyPdfkitShim() {
  const dstPkg = "target/shims/pdfkit";
  const dstJs = path.join(dstPkg, "js");
  ensureDir(dstJs);
  fs.copyFileSync("node_modules/pdfkit/package.json",
                  path.join(dstPkg, "package.json"));
  fs.cpSync("node_modules/pdfkit/js/standard-fonts",
            path.join(dstJs, "standard-fonts"), { recursive: true });
  const mjsSrc = "node_modules/pdfkit/js/pdfkit.browser.mjs";
  const mjsDst = path.join(dstJs, "pdfkit.browser.mjs");
  const raw = fs.readFileSync(mjsSrc, "utf8");
  const fallback = '"http://localhost/"';
  const baseExpr =
    `(globalThis.location ? globalThis.location.href : ${fallback})`;
  fs.writeFileSync(mjsDst, raw.replace(/import\.meta\.url/g, baseExpr));
}

copySqliteAssets();
copyYogaShim();
copyPdfkitShim();
