// 从国内镜像下载 Gradle 发行版
const https = require("https");
const fs = require("fs");
const path = require("path");

const URL_STR = process.argv[2];
const OUT_FILE = process.argv[3];
const PART = OUT_FILE + ".part";

fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });

function human(b) {
  if (b >= 1024 ** 2) return (b / 1024 ** 2).toFixed(1) + " MB";
  if (b >= 1024) return (b / 1024).toFixed(1) + " KB";
  return b + " B";
}

function download(url, dest, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 10) return reject(new Error("重定向过多"));

    let startAt = fs.existsSync(dest) ? fs.statSync(dest).size : 0;
    const headers = startAt > 0 ? { Range: `bytes=${startAt}-` } : {};

    const req = https.get(url, { headers, timeout: 60000 }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        res.resume();
        return download(res.headers.location, dest, redirects + 1)
          .then(resolve).catch(reject);
      }
      if (res.statusCode !== 200 && res.statusCode !== 206) {
        res.resume();
        return reject(new Error(`HTTP ${res.statusCode}`));
      }

      const total = res.headers["content-length"]
        ? parseInt(res.headers["content-length"], 10) + startAt : 0;

      if (startAt > 0 && res.statusCode === 206) {
        console.log(`续传：从 ${human(startAt)} 继续`);
      } else if (startAt > 0 && res.statusCode === 200) {
        console.log("服务器不支持续传，重新下载");
        startAt = 0;
        fs.rmSync(dest, { force: true });
      }

      const out = fs.createWriteStream(dest, { flags: startAt > 0 ? "a" : "w" });
      let received = startAt;
      let last = 0;

      res.on("data", (c) => {
        received += c.length;
        const now = Date.now();
        if (now - last > 700) {
          last = now;
          const pct = total ? ((received / total) * 100).toFixed(0) : "?";
          console.log(`  ${pct}%  ${human(received)}${total ? " / " + human(total) : ""}`);
        }
      });

      res.pipe(out);
      out.on("finish", () => out.close(() => {
        if (total && received !== total) {
          return reject(new Error(`大小不符：期望 ${total}，实际 ${received}`));
        }
        resolve(received);
      }));
      out.on("error", reject);
      res.on("error", reject);
    });

    req.on("timeout", () => { req.destroy(); reject(new Error("超时")); });
    req.on("error", reject);
  });
}

(async () => {
  console.log(`源:   ${URL_STR}`);
  console.log(`目标: ${OUT_FILE}\n`);
  try {
    await download(URL_STR, PART);
    fs.renameSync(PART, OUT_FILE);
    const size = fs.statSync(OUT_FILE).size;
    console.log(`\n完成：${human(size)}`);

    // ZIP 以 PK\x03\x04 开头
    const fd = fs.openSync(OUT_FILE, "r");
    const buf = Buffer.alloc(4);
    fs.readSync(fd, buf, 0, 4, 0);
    fs.closeSync(fd);
    const isZip = buf[0] === 0x50 && buf[1] === 0x4B;
    console.log(`文件头：${buf.toString("hex")} ${isZip ? "(PK ✓ 有效 ZIP)" : "(异常！)"}`);
  } catch (e) {
    console.log(`\n下载失败：${e.message}`);
    process.exit(1);
  }
})();
