# dec Remote — Android → Claude MCP

## एक बार setup, हमेशा के लिए काम

---

## Step 1: Cloudflare Worker deploy करो (5 मिनट, free, browser से)

1. https://cloudflare.com पर free account बनाओ
2. Login → **Workers & Pages** → **Create Application** → **Create Worker**
3. Editor में सब delete करो → `cloudflare_worker/worker.js` का content paste करो
4. Line 16 में `AUTH_TOKEN` बदलो: `"dec_secret_xyz123"` → अपना कोई secret डालो
5. **Save and Deploy**
6. तुम्हारा URL मिलेगा: `https://dec-remote.yourname.workers.dev`

---

## Step 2: APK build करो (GitHub Actions से — free)

**Option A: GitHub Actions (recommended)**
1. GitHub पर free account बनाओ
2. New repository बनाओ → `android_app/` folder के सारे files upload करो
3. Actions tab → "Build APK" workflow → **Run workflow**
4. 3-4 मिनट बाद → Artifacts → `dec-remote-debug.apk` download करो

**Option B: Android Studio से build करो** (अगर PC है)
```
File → Open → android_app/
Build → Build Bundle(s)/APK(s) → Build APK(s)
```

---

## Step 3: APK install करो और configure करो

1. APK install करो (Unknown sources allow करो)
2. App खोलो → fields भरो:
   - **Bot Token**: @BotFather से मिला token
   - **Chat ID**: @userinfobot से मिला ID
   - **Worker URL**: `https://dec-remote.yourname.workers.dev`
   - **Auth Token**: वही token जो worker.js में डाला
3. **START** press करो
4. Screen capture permission dialog आएगा → Allow करो

---

## Result

Telegram पर यह message आएगा:
```
🚀 dec-remote started!
📱 Device: Xiaomi Redmi Note 12
🔗 MCP URL: https://dec-remote.yourname.workers.dev/mcp

Claude Desktop config:
{
  "mcpServers": {
    "phone": {
      "url": "https://dec-remote.yourname.workers.dev/mcp",
      "type": "http"
    }
  }
}
```

Claude Desktop में config paste करो → restart → Phone connected ✅

---

## Claude में available tools

| Tool | क्या करता है |
|------|-------------|
| `get_screenshot` | Phone की latest screenshot |
| `get_keylog` | Recent keystrokes |
| `get_device_info` | Device model, Android version, screen size |
| `send_notification` | Phone पर message भेजो |

---

## Files

```
cloudflare_worker/
  worker.js          ← Cloudflare पर paste करो (HTTPS MCP server)

android_app/
  app/src/main/
    java/com/dec/remote/
      MainActivity.kt         ← UI
      RemoteService.kt        ← Background service (screenshots)
      ScreenCaptureActivity.kt← Screen permission handler
      BootReceiver.kt         ← Auto-start on boot
      CloudflareGuideDialog.kt← In-app setup guide
    res/layout/
      activity_main.xml       ← Dark UI
    AndroidManifest.xml
  .github/workflows/
    build_apk.yml    ← GitHub Actions auto-build
  build.gradle
  app/build.gradle
  settings.gradle
```
