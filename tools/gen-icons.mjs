import fs from "fs";

const root = new URL("../", import.meta.url).pathname;
const spriteFile = root + "app/src/main/java/com/nothinglondon/countdown/Sprites.kt";
const src = fs.readFileSync(spriteFile, "utf8");

const start = src.indexOf("intArrayOf(") + "intArrayOf(".length;
const body = src.slice(start, src.lastIndexOf(")"));
const sprite = (body.match(/-?\d+/g) || []).map(Number);
if (sprite.length !== 625) throw new Error(`expected 625 values, got ${sprite.length}`);

const N = 25;
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const f = (v) => (Math.round(v * 1000) / 1000).toString();

function circ(cx, cy, r) {
  return `M${f(cx - r)},${f(cy)}a${f(r)},${f(r)} 0 1,0 ${f(2 * r)},0a${f(r)},${f(r)} 0 1,0 ${f(-2 * r)},0Z`;
}

function dotsPath(viewport, diameter) {
  const scale = diameter / N;
  const origin = viewport / 2 - diameter / 2;
  const r = scale * 0.36;
  let d = "";
  let lit = 0;
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const v = clamp(sprite[y * N + x], 0, 255);
      if (v <= 0) continue;
      lit++;
      d += circ(origin + (x + 0.5) * scale, origin + (y + 0.5) * scale, r);
    }
  }
  return { d, lit };
}

function writeVector(path, viewport, layers) {
  const xml =
    `<?xml version="1.0" encoding="utf-8"?>\n` +
    `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n` +
    `    android:width="${viewport}dp"\n` +
    `    android:height="${viewport}dp"\n` +
    `    android:viewportWidth="${viewport}"\n` +
    `    android:viewportHeight="${viewport}">\n` +
    layers +
    `</vector>\n`;
  fs.writeFileSync(path, xml);
}

// --- Toy image shown in the Glyph Toys manager (512) ---
const thumb = dotsPath(512, 512);
writeVector(root + "app/src/main/res/drawable/countdown_thumbnail.xml", 512,
  `  <path\n      android:pathData="M256,256m-256,0a256,256 0 1,1 512,0a256,256 0 1,1 -512,0"\n      android:fillColor="#000000"/>\n` +
  `  <path\n      android:pathData="${thumb.d}"\n      android:fillColor="#FFFFFF"/>\n`);

// --- Launcher adaptive icon (background + foreground), 108 viewport, safe zone 72 ---
writeVector(root + "app/src/main/res/drawable/ic_launcher_background.xml", 108,
  `  <path\n      android:pathData="M0,0h108v108h-108z"\n      android:fillColor="#000000"/>\n`);

const fg = dotsPath(108, 66);
writeVector(root + "app/src/main/res/drawable/ic_launcher_foreground.xml", 108,
  `  <path\n      android:pathData="${fg.d}"\n      android:fillColor="#FFFFFF"/>\n`);

console.log(`sprite: ${sprite.filter((v) => v > 0).length} lit pixels`);
console.log(`wrote countdown_thumbnail.xml (${thumb.lit} dots), ic_launcher_background.xml, ic_launcher_foreground.xml (${fg.lit} dots)`);
