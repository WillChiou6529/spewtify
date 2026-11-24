# Spewtify

自用的 Android 音樂 App：播 YouTube Music 的歌，加上 Spotify 式的曲風／心情／情境分類。
fork 自 [ViTune](https://github.com/bartoostveen/ViTune)（ViMusic 的延續版），授權同為 GPL-3.0。

## 下載

手機瀏覽器打開：**https://github.com/WillChiou6529/spewtify/releases/latest/download/Spewtify.apk**

- 只支援 64 位元手機（arm64-v8a）
- 新版直接覆蓋安裝，收藏不會不見（簽章金鑰固定）
- 設定 > 關於 可開「檢查新版本」，有新版會通知

## 跟 ViTune 不一樣的地方

| 功能 | 在哪裡 |
|---|---|
| 繁體中文介面 | 手機語言設繁中即可 |
| 收藏自動貼曲風／心情標籤 | 背景自動跑；設定 > 資料庫 >「曲風與心情分類」看進度、立即分類、全部重新分類 |
| 收藏依曲風／心情篩選 | 收藏頁標題下方的標籤列，點了只顯示該分類，隨機播放也跟著篩選 |
| 每日 Mix | 首頁「快選」下方，每天依收藏曲風產生 2～6 個 Mix（自己的歌＋同曲風推薦） |
| 情境（讀書／運動／睡前／開車） | 首頁每日 Mix 下方，點卡片隨機播放 |
| 手動編輯分類 | 收藏的歌長按 >「編輯分類」，手動改的不會被自動分類覆蓋 |

## 分類資料從哪來

1. **YouTube Music 官方「心情與曲風」歌單反查**（不用申請）：每個分類抓前 3 個官方歌單，記下收錄的歌與歌手，7 天更新一次。
2. **依歌手推估**：歌不在官方歌單裡時，看這位歌手在官方歌單裡最常出現在哪些分類。
3. **Last.fm 標籤**（選填）：到 <https://www.last.fm/api> 免費申請 API key，貼到 設定 > 資料庫 > Last.fm API key。YouTube 分不出來的歌會再查 Last.fm。

情境由曲風／心情加權算分（規則在 `providers/tags/.../Situation.kt`），例如 Lo-fi＋放鬆 → 讀書、睡前；電子＋派對 → 運動。

## 開發

- 推 `master` → GitHub Actions（`.github/workflows/build.yml`）先跑 `providers:tags` 單元測試，再建置、簽章、發佈 Release
- 簽章金鑰與密碼放在 repo 外，透過 Secrets `SPEWTIFY_KEYSTORE_B64`／`SPEWTIFY_KEYSTORE_PASSWORD` 帶入；**金鑰不能換**，換了手機就無法覆蓋更新
- 資料庫改結構時：版本號＋1、加 `AutoMigration`，建置完從 artifact `room-schemas` 取新的 `<版本>.json` 提交回 `app/schemas/`
- 跟上游同步：`git fetch upstream && git merge upstream/master`（YouTube 改規則時上游通常會先修 yt-dlp 相關問題）
- 圖示由 `tools/make_icon.py` 產生

## 授權

GPL-3.0，沿用 ViTune／ViMusic。本專案與 YouTube、Google、Spotify 無關。
