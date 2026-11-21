# EON Mobile TV Player (Android / Kotlin / Jetpack Compose / Media3)

Пълен проект за мобилен TV плейър с поддръжка на:
- M3U / M3U8 плейлисти (URL и локални файлове) с категории (`group-title`) и логотипи (`tvg-logo`)
- HLS, DASH и MP4 потоци чрез Media3 ExoPlayer
- Външен XMLTV и `.xml.gz` EPG по `tvg-id` + автоматично фоново опресняване на всеки 6 часа чрез WorkManager
- Любими канали (`DataStore`), бързо превключване, пълноекранен режим, жестове за яркост и звук, авто-презареждане при прекъсване

## Билдване в GitHub Actions (0 RAM на твоя компютър)
1. Създай ново празно репозитори в GitHub.
2. Разархивирай този ZIP и качи всички файлове (включително скритата папка `.github/workflows/build-apk.yml`).
3. Отвори таба **Actions** в GitHub -> избери **Build EON TV Debug APK** -> след приключване свали артефакта `eon-tv-debug-apk` (`app-debug.apk`).
