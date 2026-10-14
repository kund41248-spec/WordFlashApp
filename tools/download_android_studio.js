// 下载 Android Studio 安装包（带进度、断点续传、完整性校验）
const https = require("https");
const fs = require("fs");
const path = require("path");

const URL_STR =
  "https://dl.google.com/dl/android/studio/install/2024.2.2.13/android-studio-2024.2.2.13-windows.exe";

const OUT_DIR = process.argv[2] || "D:\\英语APP开发\\installer";
const OUT_FILE = path.join(OUT_DIR, "android-studio-2024.2.2.13-windows.exe");
const PART_FILE = OUT_FILE + ".part";

fs.mkdirSync(OUT_DIR, { recursive: true });

function log(msg) {
  process.stdout.write(msg + "\n");
}

function human(bytes) {
  if (bytes >= 1024 ** 3) return (bytes / 1024 ** 3).toFixed(2) + " GB";
  if (bytes >= 1024 ** 2) return (bytes / 1024 ** 2).toFixed(1) + " MB";
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + " KB";
  return bytes + " B";
}

function download(url, dest, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 10) return reject(new Error("重定向次数过多"));

    // 断点续传：已下载的部分作为 Range 起点
    let startAt = 0;
    if (fs.existsSync(dest)) startAt = fs.statSync(dest).size;

    const headers = {};
    if (startAt > 0) headers["Range"] = `bytes=${startAt}-`;

    const req = https.get(url, { headers, timeout: 60000 }, (res) => {
      // 处理重定向
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        res.resume();
        return download(res.headers.location, dest, redirects + 1)
          .then(resolve)
          .catch(reject);
      }

      if (res.statusCode !== 200 && res.statusCode !== 206) {
        res.resume();
        return reject(new Error(`HTTP ${res.statusCode}`));
      }

      const total = res.headers["content-length"]
        ? parseInt(res.headers["content-length"], 10) + startAt
        : 0;

      if (startAt > 0 && res.statusCode === 206) {
        log(`续传：从 ${human(startAt)} 继续`);
      } else if (startAt > 0 && res.statusCode === 200) {
        // 服务器不支持 Range，只能重头来
        log("服务器不支持续传，重新下载");
        startAt = 0;
        fs.rmSync(dest, { force: true });
      }

      const out = fs.createWriteStream(dest, { flags: startAt > 0 ? "a" : "w" });
      let received = startAt;
      let lastTick = 0;

      res.on("data", (chunk) => {
        received += chunk.length;
        const now = Date.now();
        // 每 500ms 报一次进度，避免刷屏
        if (now - lastTick > 500) {
          lastTick = now;
          if (total > 0) {
            const pct = ((received / total) * 100).toFixed(1);
            log(`进度 ${pct}%  ${human(received)} / ${human(total)}`);
          } else {
            log(`已下载 ${human(received)}`);
          }
        }
      });

      res.pipe(out);

      out.on("finish", () => {
        out.close(() => {
          if (total > 0 && received !== total) {
            return reject(
              new Error(`大小不符：期望 ${total}，实际 ${received}（可重跑续传）`)
            );
          }
          resolve(received);
        });
      });

      out.on("error", reject);
      res.on("error", reject);
    });

    req.on("timeout", () => {
      req.destroy();
      reject(new Error("连接超时（可重跑续传）"));
    });
    req.on("error", reject);
  });
}

(async () => {
  log(`源: ${URL_STR}`);
  log(`目标: ${OUT_FILE}`);
  log("");

  try {
    const size = await download(URL_STR, PART_FILE);

    // 下载完成才改名为正式文件，避免中途失败留下一个"看起来完整"的文件
    fs.renameSync(PART_FILE, OUT_FILE);

    log("");
    log(`完成：${human(size)}`);
    log(`路径：${OUT_FILE}`);

    // 简单校验：Windows 可执行文件应以 MZ 开头
    const fd = fs.openSync(OUT_FILE, "r");
    const buf = Buffer.alloc(2);
    fs.readSync(fd, buf, 0, 2, 0);
    fs.closeSync(fd);
    log(`文件头：${buf.toString("hex")} ${buf.toString("ascii") === "MZ" ? "(MZ ✓ 是有效的 Windows 可执行文件)" : "(异常！)"}`);
  } catch (e) {
    log("");
    log(`下载失败：${e.message}`);
    log(`已下载部分保留在 ${PART_FILE}，重跑本脚本会自动续传。`);
    process.exit(1);
  }
})();
