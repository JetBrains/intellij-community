# WebView-as-Tauri-for-IntelliJ: стратегический отчёт

> Анализ: выдержит ли WebView-подход аргументы, которыми зарубили Compose for Desktop в IJ Platform.
> Источники: cancellation-letter по Compose, 6-month retro AI Chat-команды, POC `n500/262/light-webview-poc`, исходники `community/platform/ui.jcef/` и `community/platform/ui.webview/`, YouTrack (IJPL-184288, JBR-2751), публичные release notes, GitHub-issues и форумы по Tauri/WKWebView/WebView2/WebKitGTK.

---

## Обсуждённые вопросы (навигация)

Отчёт родился из серии вопросов. Каждый разобран ниже:

| # | Вопрос | Где ответ |
|---|---|---|
| Q1 | Могут ли мою WebView-идею зарубить теми же аргументами, что и Compose? | **Часть 1** (argument map) |
| Q2 | Как WebView отвечает на 6-месячные жалобы AI Chat команды на Compose? | **Часть 2** |
| Q3 | Что такое "два stack'а в одном окне навсегда" из письма? | **Часть 1, ряд 3** |
| Q4 | Как правильно позиционировать scope — что WebView решает и что НЕ пытается? | **Часть 5.2** |
| Q5 | Процесс-изоляция: может ли WebView уронить IDE? | **Часть 3.1, 3.2** |
| Q6 | Почему JCEF ронял IDE, а WKWebView не должен? | **Часть 3.2, 3.3** |
| Q7 | JCEF в IJ 2025.1 уже out-of-process — работает ли там native rendering? | **Часть 3.4** (ключевая находка) |
| Q8 | Почему системные WebView дают одновременно native rendering и isolation? | **Часть 3.5** |
| Q9 | Cursor, Warp — это Tauri? (поправка) | **Часть 3.7** |
| Q10 | Tauri crash-статистика — что есть? | **Часть 3.7** |
| Q11 | Технические схемы: focus interop | **Часть 8.1** |
| Q12 | Технические схемы: tab order | **Часть 8.2** |
| Q13 | Технические схемы: action handling | **Часть 8.3** |
| Q14 | Технические схемы: i18n | **Часть 8.4** |
| Q15 | DevX: как Kotlin+TS в одном дереве, как собирать, как переиспользовать UI-библиотеку | **Часть 8.5** |

---

## TL;DR

1. **Compose убили** по пяти вещам: (a) не решает главных проблем платформы (freezes, API shape, FE/BE, testability), (b) требовали seamless Swing↔Compose interop (что и дало ~30 man-years на границу), (c) нет директорского спонсора, (d) нет бизнес-метрик, (e) внешнее спонсорство слабое (Google фокусируется на Android).
2. **WebView сознательно** отрезает причину (b): **нет seamless interop, нет встраивания Swing-контролов в web и наоборот, нет наследования IJ action system / keymap внутри web**. Нужны только базовые ops (copy/paste, scroll, keyboard, focus in/out). Это срезает главный cost-driver из истории Compose.
3. **Rich UI новых фич (особенно AI-эры) часто не нуждается в старом Swing-арсенале.** AI chat команда эмпирически показала: rich editor из IJ в чате не потребовался — хватило markdown + code block. WebView позиционируется **как островки rich-UI для нового поколения фич**, а не как replacement Swing.
4. **WebView структурно решает FE/BE separation** — ровно ту цель, которую cancellation-letter прямо называл "the direction we actually want". IPC-граница делает невозможным смешивать state и rendering.
5. **6-month Compose retro** перечисляет 6 childhood diseases (selection, lists/scrolling, focus, popups, HiDPI scaling, debugging). Web-платформа структурно решает 5 из них **by construction**, не как TODO.
6. **Process isolation — сильный аргумент в пользу WebView**. Все три системных engine'а (WKWebView/WebView2/WebKitGTK) запускают renderer в отдельном процессе **by default**. Renderer crash не роняет IDE.
7. **Критический нюанс, который стоит открыть на ревью**: JCEF в IJ 2025.1 перешёл в out-of-process (`cef_server`), но **ценой принудительного OSR**. Системные WebView дают **native compositing + process isolation одновременно**, потому что это OS-level механизм, а не application-level. Это качественное превосходство, **недостижимое** для CEF-based решения в принципе.
8. **Сильные слабости WebView-proposal'а**: отсутствие Remote Dev-плана, accessibility на canvas/div-UI по умолчанию хуже Swing, стабильность WebKitGTK на Linux (честный risk), multi-engine backend scope, отсутствие директорского спонсора, отсутствие бизнес-метрик — те же (c) и (d), что убили Compose.
9. **Ключ к выживанию**: позиционировать как **"rich-UI islands + JCEF consolidation + FE/BE architecture testbed"** с явным scope **"no seamless Swing interop"**. Метрики, директорский спонсор, план консолидации JCEF → WebView с одним web engine вместо двух — обязательны **до** масштабирования.
10. **Технические схемы** для ключевых integration pain-points (focus interop, tab order, action handling, i18n, build/packaging, sharing UI lib, theme bridge, dev-server HMR, type-safe bridge, testing) проработаны в Части 8 с code-скетчами и прямой ссылкой на текущее состояние POC. Это фактическая база для ответов на техническую часть ревью.

---

## Context

В 2024-2025 JetBrains инвестировали в перевод части UI IntelliJ Platform на Compose for Desktop (Jewel). В апреле 2026 инициатива официально закрыта; куратор проекта опубликовал cancellation-letter. Параллельно команда AI Chat, которая успела отгрузить продукт на Compose, опубликовала 6-месячный retrospective с перечислением конкретных болей.

Ты поднял POC (ветка `n500/262/light-webview-poc`) с идеей "Tauri для IntelliJ Platform": системный WebView (WKWebView на macOS сейчас, WebView2/WebKitGTK позже), встроенный как native child view в Swing host, с IPC-мостом на JSON-RPC и бандленными web-ассетами. POC уже содержит:

- `WebViewFacade` / `SwingWebViewHostPanel` + macOS backend через ObjC/JNA;
- lifecycle на `CoroutineScope` (не `Disposable`), явный main-thread dispatcher;
- JSON-RPC 2.0 notifications, спроектированный upgrade path на request/response;
- demo (kanban board с 159 задачами + mermaid roadmap + полный drag-drop);
- **параллельную реализацию markdown preview** на WebView (`MarkdownWebViewHtmlPanel.kt`) — т.е. точка консолидации JCEF уже заложена.

Цель отчёта — честно пройтись по аргументам, которыми зарубили Compose, применить их к WebView, увидеть где WebView побеждает, где ловит ту же пулю, и какую позицию занять, чтобы не повторить судьбу.

---

## Часть 1. Аргументы cancellation-letter → применимость к WebView

Легенда: **WIN** — WebView структурно выигрывает; **SAME** — тот же риск и цена; **PARTIAL** — выигрывает частично; **NEW** — специфично для WebView, в письме не обсуждалось.

| # | Аргумент против Compose | Применим к WebView? | Развёрнуто |
|---|---|---|---|
| 1 | "Не решает основные проблемы IJ (freezes, API shape, FE/BE separation, testability)" | **PARTIAL** | Freezes идут из EDT-блокировок — WebView их не лечит сам по себе. Но IPC-граница **enforced** разделяет FE/BE — это буквально "the direction we actually want" из письма. Это главный reframe: WebView обслуживает стратегическую цель платформы, а не игнорирует её. |
| 2 | "Migration стоит ~30 man-years на Swing↔Compose boundary" | **WIN по scope, но не бесплатный** | Оценка 30 man-years была под требование seamless interop — встраивать Swing-контролы в Compose-сцены и наоборот. WebView **сознательно** отказывается от этого требования (см. 4.2). Осталось: focus/IME boundary, theme bridge, action delegation, testing infra, multiple engines. Это всё ещё работа, но порядок скорее 5-10 man-years для критического минимума. |
| 3 | "Два component models в одном окне навсегда" (т.е. shared focus, shared event routing, общий testing, общая документация, glue) | **SAME по coexistence, но легче по scope** | Swing не уйдёт ≥10 лет, любая история — coexistence. WebView режет seamless interop → coexistence становится "два независимых стека, соединённые тонкой границей". Плюс: Swing+JCEF **уже** coexist сегодня — WebView это консолидация (Swing+JCEF → Swing+WebView), не добавление третьего. |
| 4 | "Нет внешнего спонсора (Google фокусируется на Android, не Desktop)" | **STRONG WIN** | Системные WebView спонсируются Apple, Microsoft, Google/Apple (WKWebView / WebView2 / WebKitGTK), плюс весь web-экосистем. Это фундаментальная разница. Наш слой (bindings + IPC) — наш, но он на порядок меньше Compose Desktop. |
| 5 | "Не открывает дорогу к web и FE/BE separation" | **STRONG WIN** | Frontend — уже web. IPC — естественная FE/BE граница. То, чего письмо прямо называет целью платформы. |
| 6 | "Берём на баланс ещё один наш UI-stack" | **PARTIAL** | Web — не наш. Но bindings + bridge codegen + testing infra + theme mapping — наш слой. Это существенно меньше Compose Desktop, но не ноль. Явно ограничить scope нашего слоя (см. 4.4). |
| 7 | "Нет бизнес-метрик подтверждающих улучшения" | **SAME** | Письмо прямо назвало это провалом Compose. Критично: метрики должны быть определены **до** расширения инвестиций (см. 4.3). |
| 8 | "Нет ясного спонсора / owner / resource plan" | **SAME** | Первый аргумент в письме: "The project wasn't properly led, understaffed, without a plan". Без директорского спонсора всё остальное теряет вес. |
| 9 | "Долгий coexistence дороже чем кажется (два component models, два testing, glue, docs)" | **SAME, но с exit-историей** | Если позиционировать WebView как replacement JCEF (консолидация), а не как параллельный Swing-replacement, общий bill of materials уменьшается. В долгосрочной перспективе: Swing + WebView вместо Swing + JCEF + Compose. |

### Ключевой reframe

Cancellation-letter прямо говорит: `"The strategic work should go into separating frontend from backend, improving APIs, improving testability, and addressing performance and architecture bottlenecks directly."`

**WebView структурно обслуживает первый пункт этой программы**. Compose не обслуживал ни одного — это и было его смертным приговором. Если позиционировать WebView как **технологический testbed для FE/BE separation платформы**, а не как новый UI framework, аргумент #1 (самый сильный в письме) из "пробой" превращается в "союзник".

---

## Часть 2. 6-month Compose retro → применимость к WebView

Почти все "childhood diseases" Compose из retrospective решены в WebView by design, а не как TODO.

| Compose pain | WebView status | Обоснование |
|---|---|---|
| **Text selection** — "приходится прицеливаться" | **STRONG WIN** | Browser selection 30+ лет зрелости: IME composition, clipboard, shift-click ranges, double/triple-click слова/параграфы. В WebView не существует как проблема. Это **самый важный childhood disease для chat** и он решён. |
| **Lazy list / scrolling** — странное поведение | **STRONG WIN** | Virtualization libs (react-virtual, TanStack Virtual) в production у тысяч приложений; scroll аппаратно ускорен в native WebView. Для chat с 10k сообщений уже есть готовые решения. |
| **Focus / keyboard navigation** — слабая | **PARTIAL WIN** | Внутри web focus/tab-order решены. **Но**: фокус через Swing↔WebView boundary требует интеграции. У тебя в POC-0 decision `05-focus-keyboard-ime.md` это явно помечено как non-trivial. Решаемо, но не бесплатно. |
| **Popups / context menus** — вручную | **PARTIAL WIN** | HTML Popover API + CSS решают визуальный контекст из коробки. **Но**: интеграция с IntelliJ action system (регистрация actions, shortcuts, DataContext) — это bridge-работа. Согласно 4.2 — и не должны её делать в полном объёме, только базовый copy/paste. |
| **Scaling / HiDPI** — умножать на коэффициенты | **STRONG WIN** | `devicePixelRatio` + CSS `rem` + viewport meta — стандарт. WKWebView / WebView2 / WebKitGTK наследуют system DPI корректно. Это решённая задача web-платформы. |
| **Debugging** — recomposition chain трудно читать | **STRONG WIN** | Full Chrome DevTools: Elements, Timeline, Performance, Memory profiler, React DevTools, Coverage. Это буквально лучший debugger в индустрии после разве что Rider. |
| **Testing infra с нуля** | **PARTIAL** | Web-testing (vitest, playwright, @testing-library) — зрелые framework'и. **Но** интеграция с IJ TeamCity, screenshot-diff через git, Swing↔WebView e2e — всё ещё надо строить. Честнее чем "бесплатно", но не с нуля как у Jewel. |

**Вывод**: твой аргумент "web — самый богатый рендерер современного UI" подкреплён именно этим retro. WebView лечит **5 из 6** childhood diseases Compose структурно. Оставшиеся два (focus boundary, popup→action integration) — ограниченной scope работа.

---

## Часть 3. Process isolation, crash-характеристики и уникальное преимущество системных WebView

Один из ожидаемых аргументов ревьюера: "WebView может уронить IDE". Фактические данные по всем трём platform engines, прецеденты JetBrains, и архитектурные отличия system WebView от JCEF показывают, что process isolation — **сильный аргумент в пользу** WebView, и даже уникально отличающий его от JCEF OOP.

### 3.1. Модель изоляции на каждой платформе

| Engine | Архитектура процессов | Renderer crash → host | Host recovery API |
|---|---|---|---|
| **WKWebView** (macOS) | С 2014 года renderer живёт в отдельном **WebContent process**. iOS 13+: новый WebContent process на каждый `WKWebView` instance (до лимита пула). JIT, DOM, painting — всё в нём. | Нет — host остаётся жив, content превращается в blank view. | Делегат `webViewWebContentProcessDidTerminate` (уже в POC-0 плане, pending на 2026-04-21). Edge case (WebKit bug 176855): callback иногда unreliable — требует defensive code с тайм-аутами. |
| **WebView2** (Windows) | Multi-process by design: один browser process на UserDataFolder + N renderer + GPU process + helpers. | Renderer crash → содержимое заменяется error page, host получает `ProcessFailed(RenderProcessExited \| FrameRenderProcessExited)`. Host жив. | `Reload()` API или `Close+recreate`. Browser-process crash серьёзнее: закроет все WebView2-controls под этой UserDataFolder, host получает `BrowserProcessExited` и должен пересоздать controls. Но browser-process crash редок. |
| **WebKitGTK** (Linux) | Три процесса: UI / Network / Web. Renderer crash изолирован. Встроен hang detection: приложение получает notification об unresponsive web process. | Host жив. | Аналогично WKWebView — reload content или recreate view. **Но**: WebKitGTK репутационно менее стабильный, Tauri-сообщество документирует деградацию стабильности от релиза к релизу. Это honest risk для Linux backend. |

**Summary**: на всех трёх платформах **renderer в отдельном процессе by default**. Это platform guarantee, а не что-то, что мы строим.

### 3.2. Где IDE всё ещё может упасть — и почему это узкий risk

Процесс WebContent — изолирован. Но **native bridge code** (`MacWebViewFacade.kt`, `WKWebViewBridge.kt`, `MacMainThreadDispatcher.kt`) живёт в JVM процессе IDE. Если там bug — неправильный ObjC selector, bad JNA cast, use-after-free через retain/release, race с main thread — это SIGSEGV **в IDE process**, не в renderer.

Это **наша** native surface, и именно её надо держать:
- **Минимальной по объёму** — thin slice над `WebKit.framework`;
- **Хорошо протестированной** — POC уже включает stress-тест на 100 циклов create/dispose без crash;
- **Документированной threading-контрактом** — декларированное разделение EDT / macOS main thread / JS thread.

Это ограниченная задача. JavaScript, DOM, rendering — **не могут** уронить IDE через WKWebView. Это архитектурная гарантия OS.

### 3.3. Почему JCEF исторически ронял IDE

CEF тоже multi-process (browser + N renderer + GPU). В теории renderer crash не должен убивать host. Но JCEF ронял по другим причинам:

1. **In-process интеграция с JBR (до IJ 2025.1).** `libjcef.so` + `libcef.so` грузились в JVM процесс. Большая custom native surface — loader, JVM embedding, message routing, GPU bridge — жила в host. Crash там = crash IDE.
2. **Native bugs в `libcef.so` на Linux.** Задокументирован `JBR-2751`: "Markdown crashes IDE with JCEF enabled on Linux — buffer overflow detected". Это падение native кода в host-процессе, не в subprocess.
3. **GPU / `cef_server` сваливались на Linux** — особенно под Wayland (KDE-community жаловались публично).
4. **`JBCefJSQuery` становился GC root** — не crash, но стабильная утечка → OOM → IDE down. Workaround через `Disposer` задокументирован в Jupyter SVG output `JupyterSvgOutputComponentFactory.kt` явным комментарием "Mitigating a memory leak inside JBCefJSQuery$1, which becomes a GC Root and never disappears".
5. **IJ 2025.1 явно перевели JCEF в out-of-process** — прямое признание проблемы и её фикс. Прецедент внутри платформы.

### 3.4. Критическая находка: JCEF OOP в IJ 2025.1 вынуждает OSR

Исходник `community/platform/ui.jcef/jcef/JBCefBrowserBuilder.java:38-48`:

```java
public @NotNull JBCefBrowserBuilder setOffScreenRendering(boolean isOffScreenRendering) {
    if (!isOffScreenRendering) {
      if (JBCefApp.getInstance().isRemoteEnabled()) {
        Logger.getInstance(JBCefBrowserBuilder.class).warn(
          "Trying to create windowed browser when remote-mode is enabled. Settings isOffScreenRendering=false will be ignored.");
        myIsOffScreenRendering = true;
        return this;
      }
    }
    myIsOffScreenRendering = isOffScreenRendering;
    return this;
}
```

То есть **в out-of-process JCEF (remote-mode) native/windowed rendering принудительно выключается и заменяется на OSR**. Настройка `setOffScreenRendering(false)` игнорируется с warning. YouTrack подтверждает это поведением: `IJPL-184288` — "Can't create remote browser with rendering Windowed/rendering error if jcef.out-of-process enabled".

Т.е. текущий state-of-the-art JCEF в IJ 2025.1 вынужден выбирать одно из двух:

| Режим | Rendering | Crash isolation | Performance |
|---|---|---|---|
| In-process JCEF | Native (heavyweight AWT Canvas) | Плохая (libcef.so live в JVM) | Лучше (нет per-frame readback) |
| OOP JCEF (2025.1) | **OSR только** (buffer copy через IPC) | Хорошая (cef_server отдельный процесс) | Хуже (OSR texture transfer + CPU copy) |

### 3.5. Почему системные WebView строго доминируют JCEF OOP

CEF как библиотека **не умеет cross-process native compositing**. Host-процесс и renderer-процесс сидят в разных window-manager contexts, и единственный способ доставить пиксели — memcpy через IPC. Это фундаментальное ограничение CEF.

Системные WebView-engines используют **OS-level cross-process compositing**, которого у CEF нет:

- **macOS (WKWebView)**: WebContent process рендерит в `IOSurface` (shared GPU texture). Host получает `CALayer`, который samples этот IOSurface; WindowServer композитит CALayer-tree **через границу процессов** средствами CoreAnimation. JVM не копирует пиксели — WindowServer делает GPU-compositing напрямую.
- **Windows (WebView2)**: аналогично через `DirectComposition` / `DCompositionVisual`. Host подмешивает child visual из renderer-процесса в свой compositional tree. DWM композитит.
- **Linux (WebKitGTK)**: Wayland subsurfaces / X11 compositor handles; compositor сливает контент через window system.

Это то, что OS даёт бесплатно и что CEF/JCEF не смогли себе сделать.

**Следствие для proposal**:

> JCEF в 2025.1 пришёл к out-of-process, но ценой принудительного OSR. Системные WebView дают **native compositing + process isolation одновременно**, потому что это OS-level механизм, а не application-level. Это качественное превосходство, недостижимое для CEF-based решения в принципе.

Это переводит дискуссию "WebView vs JCEF OOP" из "два варианта, выбирайте" в **"WKWebView-path строго доминирует по дереву rendering × isolation"**. Это один из самых сильных технических аргументов в пользу proposal.

### 3.6. Memory-характеристики как бонус

Renderer heap изолирован от IDE heap. Утечка в web-контенте не кормит IDE OOM. Прецедент `JBCefJSQuery` memory leak (Jupyter SVG outputs) — это типичный in-process leak, где JS-замыкания держат JVM GC roots. IPC-граница с явным Kotlin↔JSON переходом отсекает этот класс проблем структурно.

### 3.7. Tauri crash stats и поправка про Cursor/Warp

Формального ежегодного отчёта по crash rate Tauri нет в публичном доступе (апрель 2026). На GitHub-issues видны точечные проблемы:

- Linux WebKitGTK instability (Tauri discussion #8524 прямо называет её "totally unstable");
- random freezes на Windows WebView2 (issue #13498);
- renderer status access violations (issue #11432).

**Поправка**: Cursor и Warp — **не Tauri**:
- **Cursor** — форк VS Code, значит **Electron** (Chromium + Node.js в одном процессе).
- **Warp** — нативный Rust со своим GPU-рендерером поверх Metal, без web-технологий.

Production-proof именно на Tauri тоньше, чем на Electron. Заметные Tauri-продукты: Spacedrive, AppFlowy, Pot — но не household names. Для референсов "WebView для rich UI работает в продакшне" лучше брать **Electron-экосистему** (VS Code, Slack, Discord, Obsidian, Figma Desktop, GitHub Desktop, 1Password) — паттерн тот же (web UI + JVM/native backend), только bundled Chromium вместо system WebView.

**Выводы из stats**:
- macOS WKWebView + Windows WebView2 — production-ready, хотя recovery path обязан быть реализован.
- Linux WebKitGTK требует особого внимания и/или альтернативного backend.
- Это поддерживает **macOS-first POC** (ты уже так сделал), но не закрывает вопрос Linux/Windows scope.

### 3.8. Что из process isolation надо донести в proposal

1. **Renderer crash ≠ IDE crash** на всех трёх платформах (OS-level guarantee).
2. **POC-0 `09-crash-leak-diagnostics.md` уже планирует** `webViewWebContentProcessDidTerminate` observer (pending на 2026-04-21) — recovery-путь с первого дня.
3. **JCEF out-of-process (IJ 2025.1)** — прецедент что платформа умеет и принимает multi-process модель.
4. **Однако**: JCEF OOP достиг isolation ценой native rendering. Системные WebView дают **оба** — это key качественное отличие.
5. **Memory-характеристики лучше** — IPC-граница отсекает `JBCefJSQuery`-класс host-side утечек.
6. **Linux WebKitGTK — честно признать как risk**. Backend для Linux потребует выбора (WebKitGTK vs CEF-based fallback vs что-то ещё). Не маскировать.

---

## Часть 4. Уникальные риски WebView, которые в cancellation-letter НЕ обсуждались

Эти свежие для ревьюера, и именно на них его аргументы будут целиться, если инициатива пойдёт дальше.

1. **Remote Dev story отсутствует.** POC-0 явно фиксирует: native child view не перешагивает через удалённый рабочий стол. IJ Gateway / Code With Me — огромная инвестиция JetBrains. Если WebView = no remote — это blocker калибра cancellation-letter. Нужен явный план:
   - FE/BE split сам по себе открывает путь (frontend крутится на клиенте Gateway, backend по RPC к серверу);
   - для island-режима в классическом remote — либо OSR fallback для renderer, либо carve-out "используйте Swing в remote case".

2. **Accessibility.** Swing в IntelliJ прошёл enterprise-аудиты (US gov, medical, enterprise customers). WKWebView с VoiceOver работает, но custom-UI на canvas/div'ах **без ARIA** — дефолтно не accessible. Нужен explicit commitment к ARIA-audit + VoiceOver/NVDA smoke tests в success criteria POC-1.

3. **Fonts / theme pixel-parity.** JetBrains font, IntelliJ theme, focus rings, scrollbars, selection colours — всё должно совпадать 1:1 с Swing-соседями. Это boundary-работа: CSS theme bridge + JBTheme → CSS-vars mapping. Не сложно, но не бесплатно.

   Important: focus, IME, shortcuts, resize, z-order, and hide/show are **not unique WebView risks compared with JCEF windowed mode**. They are the same Swing/native-browser boundary class that JCEF has been paying for through `JcefShortcutProvider`, keyboard reposting, OSR/windowed modes, context-menu handling, and accumulated production fixes. For system WebView, the difference is that this maturity layer has to be rebuilt for `WKWebView`, `WebView2`, and `WebKitGTK`.

4. **Security / supply chain.** WebView + npm = потенциальная атака. Нужна policy:
   - только bundled assets в production, no external CDN (текущий POC тянет React с CDN — OK для demo, но **критично** убрать для final framing);
   - strict CSP: `script-src 'self'`, `object-src 'none'`, nonce-based inline;
   - no `eval` в bridge;
   - audited npm dependencies через lock files + SCA в CI.

5. **Crash diagnostics.** Хорошая новость: renderer crash не убивает IDE (Часть 3). Требуется:
   - интеграция `webViewWebContentProcessDidTerminate` с IJ problem reporter / fus events;
   - recovery UX: blank view → reload button / auto-reload + telemetry;
   - иначе renderer-crash становится невидимым в телеметрии и плохим UX.

6. **Plugin-author onboarding tax.** TS + bundler (Vite) + bridge SDK + testing framework = новый набор навыков для plugin-авторов. Не смертельно (web-скиллы массовые в индустрии), но IJ-specific библиотеки учиться придётся.

7. **OS/runtime update volatility.** JCEF ships its engine with the IDE/JBR, so browser behavior changes mainly with JetBrains-controlled IDE/JBR releases. System WebView engines live with the OS or runtime: `WKWebView` changes with macOS/Safari updates, WebView2 Evergreen can update independently of the IDE, and WebKitGTK changes with distribution packages. This raises the risk of sudden rendering, input, and runtime regressions, so fallback and telemetry are mandatory.

   Detailed risk memo: [System WebView OS Update and JCEF Risk Comparison](System-WebView-OS-Update-Risk.md).

8. **Single platform today.** POC-0 — только macOS. WKWebView ≠ WebView2 ≠ WebKitGTK по поведению, lifecycle, IPC, resource loading. План multi-engine backend — 2-3x оригинальной scope. Сейчас это уязвимость в стратегическом аргументе и слабость для "когда готово?" вопроса.

---

## Часть 5. Framing: как НЕ получить судьбу Compose

Cancellation-letter убил Compose по совокупности: (a) нет ясной проблемы, которую только он решает, (b) нет спонсора, (c) нет метрик, (d) scope crawl в сторону "replace Swing", (e) требования seamless interop. Чтобы не повторить — инвертируй каждое из этих явно.

### 5.1. Reframe: "rich-UI islands" и "JCEF consolidation", НЕ "новый способ писать UI"

- **НЕ говори**: "давайте переведём всё на web".
- **Говори**: "для UI-компонентов, которые по своей природе web-подобные (chat, markdown preview, diagrams, roadmaps, product tours, welcome screens, dashboards, AI-chat interfaces) — у нас уже 10 лет используется JCEF. Предлагаю эволюционировать его: заменить JCEF-based решения на более лёгкий, быстрый и стабильный путь через системные WebView, который одновременно структурно enforces FE/BE separation — то, что нужно платформе по roadmap'у".

Это превращает proposal из "Swing replacement" (автоматически expensive, автоматически уязвимый) в **"JCEF evolution + FE/BE architecture testbed"**.

### 5.2. Явно сформулируй что WebView решает и что НЕ пытается решить

Cancellation-letter убил Compose фразой "does not solve the core problems". Но не менее важная часть — что от Compose **требовали seamless Swing↔Compose interop** (встраивать Swing-контролы внутрь Compose-сцен и наоборот, чтобы миграция была незаметной). Это и породило 30 man-years оценки границы.

**Явно скажи заранее: WebView НЕ пытается быть этим:**

- **Не** seamless replacement для Swing, где переход "никто не заметит".
- **Не** гибридная сцена, где внутри WebView встроен Swing-control (rich editor, file tree, tool window) или где в Swing-панель встроен web-view с Swing-children. Двустороннего interop нет.
- **Не** наследование IntelliJ action system, context menu framework, keymap, theming — на стороне web-контента. Встраивать IJ shortcuts и action context в web не нужно: базовых ops достаточно (copy/paste, scroll, keyboard input, focus in/out, paste, find-in-page).
- **Не** замена сотен существующих Swing-панелей IDE. Swing остаётся основным UI stack'ом **навсегда** для большинства tool windows.

**Что WebView даёт уникально и чего никто другой не даёт:**

1. **Rich UI слой для новых фич AI-эры** — streaming chat, markdown + code + diagrams + interactive cards, onboarding flows, dashboards, timeline/gantt/kanban, product tours, настройки-как-прогрессивное-UI. Это тот класс UI, который в индустрии **пишется преимущественно на web**, и где Swing/Compose буквально не дотягивают по визуальной палитре и скорости разработки.
2. **Старые "богатые" компоненты платформы часто не нужны в новых фичах.** Эмпирика из AI chat retrospective: rich editor из IJ, который бы дал полное syntax highlighting и smart-editing — **не потребовался**. Достаточно markdown + code block с базовой подсветкой через highlight.js / Shiki / Prism. Это паттерн: новые фичи не требуют глубокой интеграции со старым Swing-инвентарём.
3. **AI-генерация UI качественно.** LLM-агенты на порядок лучше пишут React/HTML/CSS чем Kotlin/Swing. Для фич, где velocity критична (AI-инструментарий, экспериментальные UX, A/B-тесты UI), это материальное преимущество velocity.
4. **Forcing function для FE/BE separation.** IPC-граница делает невозможным смешивать state и rendering в одной клетке. Ровно то, что cancellation-letter называл целью платформы.
5. **Plugin ecosystem unlock.** Миллионы web-разработчиков могут писать плагины (vs тысячи Swing/Compose разработчиков). Низкий барьер входа.
6. **Process isolation + native compositing одновременно** — уникально системный-WebView путь, недостижимое преимущество относительно JCEF OOP (см. 3.4–3.5).

**Эмоциональный ответ на возможное "а почему не Compose?":** Compose упал на гибридной интеграции со Swing. WebView сознательно режет этот узел — rich UI без попытки бесшовно дружить со Swing. Узкая scope = дешевле граница = выживаемое proposal.

### 5.3. Определи метрики **до** инвестиций

Cancellation-letter прямо называет отсутствие бизнес-метрик провалом Compose. WebView должен определить метрики upfront:

- **Performance**: FPS ≥ 60 при streaming chat с ≥200 сообщениями; startup latency панели измерять отдельно для system WebView и JCEF. Текущий локальный baseline: system WebView около 1 секунды cold startup, JCEF in-process около 10-15 секунд cold startup, поэтому JCEF не должен считаться lightweight startup path. Memory overhead WebView instance < 50 MB incremental.
- **Feature velocity**: MVP chat-подобной фичи ≤ X человеко-недель vs current Compose baseline (требует согласования числа).
- **Regression rate**: < 50% регрессий per-feature Swing-chat за первый год (challenge number, согласовать с AI Assistant team).
- **AI test pass rate**: агент пишет minor feature, passes vitest + playwright smoke, за время ≤ сопоставимое с Kotlin/Swing для эквивалентной фичи.
- **Plugin author Time-To-First-Feature**: внешний разработчик с web-фоном делает minimal plugin за один день, включая setup, sample, bundle, install.
- **Crash rate**: renderer crashes per session < X, ни один не приводит к IDE crash.

### 5.4. Consolidation story, НЕ "yet another stack"

- **Сформулировать конец пути**: Swing + один web engine (system WebView), а не Swing + JCEF + Compose + WebView.
- **Milestone-план**:
  - POC-0 (текущий): WKWebView на macOS, proof of rendering/interactivity viability.
  - POC-1: request/response RPC, custom scheme handler, основной use case (markdown preview или AI chat), accessibility/security/theme parity в success criteria.
  - POC-2: WebView2 на Windows, second use case (второй JCEF-клиент мигрирован).
  - POC-3: WebKitGTK на Linux или CEF-based fallback; Linux-specific stability investigation.
  - GA: JCEF-клиенты мигрированы, JCEF в deprecation; Swing остаётся основным UI-stack'ом для всего остального.
- **Message**: "со временем −1 stack" (JCEF уйдёт), не "+1 stack".

### 5.5. Найди директорского-уровня спонсора

Cancellation-letter буквально: `"The project wasn't properly led, and as a result it was run without a plan and was understaffed"`. Это **первый** аргумент в письме. Без спонсора всё остальное теряет вес.

**Кандидаты**:
- Кто отвечает за AI Assistant (прямой beneficiary, 6-month retro даёт материал);
- Кто отвечает за platform FE/BE split roadmap (WebView обслуживает этот план прямо);
- Кто отвечает за JCEF сегодня (консолидация логично проходит через них);
- Кто отвечает за plugin ecosystem / marketplace (WebView открывает новые плагин-сегменты).

Лучший вариант — cross-departmental спонсорство: AI Assistant dir + Platform dir, с явным письменным commitment к ресурсам и срокам POC-1.

### 5.6. Явный Remote Dev план

- **Вариант A (идеальный)**: WebView = client-side рендер в Gateway-world. FE/BE split решает remote сам собой — frontend всегда на клиенте, backend может быть хоть где.
- **Вариант B (carve-out)**: "для island-use case в classic remote JCEF/Swing остаётся fallback'ом, WebView — только локальный случай". Честно объявить ограничение.

Не оставляй это open question для ревью.

### 5.7. Accessibility commitment с первого дня

Не в TODO, а в success criteria POC-1:
- ARIA-audit через axe-core в CI;
- VoiceOver smoke tests на macOS;
- Keyboard-only navigation для всех interactive элементов demo;
- Focus ring visible и консистентен со Swing-соседями.

Иначе аргумент "WebView accessibility хуже Swing" всплывёт на review и потребует защиты.

### 5.8. Security posture

Явная policy, задокументированная до того, как ревьюер заметит CDN в demo:
- Bundled assets only в production; CDN — только в demo/dev mode за flag.
- Strict CSP (`Content-Security-Policy: default-src 'self'; script-src 'self'`).
- No inline `eval`, no `Function(...)`, no dynamic import of remote.
- Audited npm-зависимости: lock file в VCS, renovate/dependabot для критичных, SCA в CI.
- Bridge endpoints: typed schema, no `postMessage(anyJson)` без валидации.

---

## Часть 6. De-risk actions (план что делать дальше)

1. **Сформулировать proposal-memo** именно как "rich-UI islands + JCEF consolidation + FE/BE testbed", не как UI-stack replacement. Это не код — это текст, но это **основная** работа, от которой зависит судьба.
2. **Выбрать pilot use case**, где:
   - (a) Swing явно неадекватен или JCEF уже используется;
   - (b) бизнес-ценность видимая;
   - (c) ограниченный scope для POC-1.
   Два наиболее logical варианта:
   - **Markdown preview**: JCEF уже используется, в POC уже есть параллельная WebView реализация — консолидация очевидна, risk низкий.
   - **AI chat**: 6-month retro прямо указывает на боли, WebView структурно их решает, бизнес-ценность высокая, но scope больше.
3. **Определить метрики** (см. 5.3) — согласовать с потенциальным спонсором **до** расширения scope.
4. **Multi-platform roadmap** на бумаге: когда WKWebView → когда WebView2 → когда WebKitGTK/CEF backend. Если не распишешь — скажут "macOS-only experiment".
5. **Ответ на Remote Dev** в одном абзаце (5.6). Не нужно решать, нужно показать что подумано.
6. **Accessibility / Security / Theme parity** — чеклист в POC-1 success criteria.
7. **Убрать CDN** из demo до reviewable state, или явно пометить как "demo-only, production будет bundled".
8. **Не расширяй POC в сторону "универсальной рамки"**, пока proposal не одобрен. Cancellation-letter прямо описал ловушку understaffing + no plan = death spiral.
9. **Подготовить техническое one-pager** с тремя ключевыми техническими находками:
   - Системный WebView = native compositing + process isolation (JCEF OOP отказывается от native rendering);
   - Renderer crash ≠ IDE crash на всех трёх платформах (OS guarantee);
   - POC уже решает 5/6 childhood diseases Compose из retro by construction.

---

## Часть 7. Открытые вопросы

1. **Какой сценарий "первый реальный клиент"** — markdown preview (низкий риск, уже половина сделана, JCEF consolidation story), AI chat (высокая бизнес-ценность + подкреплено retro, но больше scope), или что-то новое?
2. **Есть ли уже потенциальный директорский спонсор**, или это часть работы?
3. **Насколько готов лично формулировать proposal как "JCEF consolidation + FE/BE testbed"**, а не как "новая философия UI"? Первое защитимо, второе повторит судьбу Compose.
4. **Remote Dev** — текущая интуиция: решать через FE/BE split, или явный carve-out для island-use case?
5. **Бюджет времени до proposal-ревью** — сколько недель на подготовку memo, метрик, POC-1 scope?

---

## Часть 8. Технические схемы интеграции ключевых pain-points

Для защиты proposal'а на ревью нужны **конкретные** технические схемы по самым сложным integration-местам. Ниже — скетчи, основанные на текущем POC, существующих IJ-паттернах (IdeFocusManager, DynamicBundle, ActionManager) и опыте Electron/Tauri-приложений.

### 8.1. Focus interop: Swing ↔ WebView

**Модель**: WebView — атомарная focus-единица для Swing, но внутри неё DOM-focus-traversal свой. Граница — одна "дверь" на вход и одна на выход, управляется через IPC-boundary события.

**Текущее состояние POC-0** (decision 05 Partial): `requestWebViewFocus()` / `clearWebViewFocus()` hooks есть в `SwingWebViewHost`, native input ownership реализовано (WKWebView — first responder). Tab-boundary делегирование **не реализовано** (pending).

**Incoming focus (Swing → WebView)**:

```kotlin
// SwingWebViewHostPanel.kt
override fun requestFocus() {
  super.requestFocus()       // JPanel получает Swing focus
  facade.requestWebViewFocus()  // делаем WKWebView first responder
}

init {
  addFocusListener(object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      // Различаем TAB и click по cause:
      val entry = when (e.cause) {
        FocusEvent.Cause.TRAVERSAL_FORWARD  -> "first"
        FocusEvent.Cause.TRAVERSAL_BACKWARD -> "last"
        else -> "auto"   // mouse click → WKWebView сам получит focus через native
      }
      facade.messageBus.publish("host/focusEntry", FocusEntry(entry))
      facade.requestWebViewFocus()
    }
  })
}
```

JS сторона:

```ts
rpc.on('host/focusEntry', ({ direction }) => {
  const tabbables = getTabbables(document.body);  // :focusable CSS-selector trick
  const target = direction === 'last' ? tabbables.at(-1) : tabbables[0];
  target?.focus();
});
```

**Outgoing focus (WebView → Swing)**:

```ts
// bridge/focus.ts
document.addEventListener('keydown', (e) => {
  if (e.key !== 'Tab') return;
  const tabbables = getTabbables(document.body);
  const active = document.activeElement;
  const atFirst = active === tabbables[0];
  const atLast  = active === tabbables.at(-1);
  if (e.shiftKey && atFirst) {
    e.preventDefault();
    rpc.notify('host/focusExit', { direction: 'prev' });
  } else if (!e.shiftKey && atLast) {
    e.preventDefault();
    rpc.notify('host/focusExit', { direction: 'next' });
  }
});
```

```kotlin
// SwingWebViewHost.kt
bus.subscribe(scope, "host/focusExit") { payload: FocusExit ->
  withContext(Dispatchers.EDT) {
    facade.clearWebViewFocus()  // снимаем WKWebView с responder
    val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    when (payload.direction) {
      "next" -> kfm.focusNextComponent(hostPanel)
      "prev" -> kfm.focusPreviousComponent(hostPanel)
    }
  }
}
```

**IME**: решается автоматически через native input ownership (текущий POC-0 уже работает для EN + хотя бы один IME language). Не требует специального bridge'а — native responder chain доставляет inputMethodEvent прямо в WKWebView.

**Known edge cases**:
- Focus trap если `tabbables` пуст (модал без кнопок). Решение: всегда есть fallback-target `document.body` с `tabindex="-1"` + `host/focusExit` если Tab на body.
- Popup overlays (IJ's JBPopup) перекрывают WebView: их focus Swing-native, возвращение фокуса в WebView — через стандартный `IdeFocusManager.requestFocus(hostPanel)`.

### 8.2. Tab order

Swing видит `SwingWebViewHostPanel` как **одну** остановку в `FocusTraversalPolicy`. Внутри WebView DOM-порядок управляется через HTML-разметку + `tabindex`.

**Интеграция с кастомным FocusTraversalPolicy** (например, toolbar → WebView → status bar):

```kotlin
// ToolWindow content panel
class MyFeatureContentPanel : JPanel(BorderLayout()) {
  init {
    val toolbar = createToolbar()
    val hostPanel = SwingWebViewHostPanel(facade)
    val status   = createStatusBar()
    add(toolbar, BorderLayout.NORTH)
    add(hostPanel, BorderLayout.CENTER)
    add(status, BorderLayout.SOUTH)

    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = ComponentsListFocusTraversalPolicy(
      listOf(toolbar, hostPanel, status)
    )
  }
}
```

WebView-внутренний tab-order — стандартный web: DOM-order + `tabindex > 0` переопределение + `role` ARIA-семантика (необходимо для accessibility; см. 4.2).

### 8.3. Action handling: базовый уровень

Согласно явной позиции proposal'а (5.2): **не** проксируем полный IJ action system в WebView. Не передаём `DataContext` / `PsiElement` / Editor-selection. Разрешаем две узких модели:

**8.3.1. JS вызывает именованную business-команду**

Предпочтительный путь для 90% случаев. Command registry в Kotlin, явные request/response типы:

```kotlin
// MyFeatureCommands.kt
@Serializable data class SaveDraftRequest(val text: String, val draftId: String?)
@Serializable data class SaveDraftResponse(val draftId: String)

class MyFeatureCommands(private val service: DraftService, private val bus: WebViewMessageBus) {
  init {
    bus.registerCommand<SaveDraftRequest, SaveDraftResponse>("feature/saveDraft") { req ->
      SaveDraftResponse(service.save(req.text, req.draftId))
    }
    bus.registerCommand<Unit, DraftList>("feature/listDrafts") { _ ->
      service.list()
    }
  }
}
```

JS:

```ts
const resp = await rpc.invoke<SaveDraftRequest, SaveDraftResponse>(
  'feature/saveDraft',
  { text: '...', draftId: null }
);
```

**8.3.2. JS триггерит существующий AnAction** (редко и осторожно)

Для ограниченных кейсов — открыть Settings, показать Find-in-Path и т.п. Явный method, не скрытый:

```kotlin
@Serializable data class InvokeActionRequest(val actionId: String)

bus.registerCommand<InvokeActionRequest, Unit>("ide/invokeAction") { req ->
  val action = ActionManager.getInstance().getAction(req.actionId)
    ?: error("No action: ${req.actionId}")
  withContext(Dispatchers.EDT) {
    val dataContext = DataManager.getInstance().getDataContext(hostPanel)
    val event = AnActionEvent.createFromDataContext("WebView", null, dataContext)
    ActionManager.getInstance().tryToExecute(action, null, null, "WebView", true)
  }
}
```

Явный allowlist action IDs в proposal (не wildcard). Без `DataContext` проброса в сам web.

**8.3.3. Kotlin публикует IDE-события в JS**

Односторонняя подписка на изменения IDE state — theme, locale, active project, и т.п.

```kotlin
class MyFeatureEventPublisher(private val bus: WebViewMessageBus, private val scope: CoroutineScope) {
  init {
    scope.launch {
      messageBusConnection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheme ->
        bus.publish("ide/themeChanged", ThemeSnapshot.fromCurrent(scheme))
      })
    }
  }
}
```

JS:

```ts
rpc.on('ide/themeChanged', (snap) => {
  document.documentElement.style.setProperty('--ij-bg', snap.backgroundColor);
  // ...
});
```

**Browser-native actions** (copy, paste, select-all, find-in-page) — работают без bridge'а, стандартный AppKit / DWM routing. Это и есть "базовые ops", о которых говорится в 5.2.

### 8.4. i18n: единый источник правды на Kotlin-стороне

IJ стандарт: `*Bundle.kt` + `messages/*.properties` + `DynamicBundle`. Уже используется в POC (`WebViewDemoBundle`). Расширение для web-контента:

**Паттерн**: Kotlin — единственный источник. JS получает снапшот строк на init + при смене локали.

```kotlin
// MyFeatureBundle.kt
object MyFeatureBundle {
  const val BUNDLE = "messages.MyFeatureBundle"
  private val INSTANCE = DynamicBundle(MyFeatureBundle::class.java, BUNDLE)

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String =
    INSTANCE.getMessage(key, *params)

  fun snapshot(): Map<String, String> {
    val rb = ResourceBundle.getBundle(BUNDLE, LocalizationOrder.getResolveLocale())
    return rb.keySet().associateWith { rb.getString(it) }
  }
}

// Inside MyFeaturePanel
bus.publish("i18n/snapshot", MyFeatureBundle.snapshot())

// Re-publish when IDE locale changes
LocalizationStateService.getInstance().addListener {
  bus.publish("i18n/snapshot", MyFeatureBundle.snapshot())
}
```

JS:

```ts
let strings: Record<string, string> = {};
rpc.on('i18n/snapshot', (data) => { strings = data; });

export function t(key: string, ...params: string[]): string {
  let out = strings[key] ?? `[${key}]`;
  params.forEach((p, i) => { out = out.replace(`{${i}}`, p); });
  return out;
}

// Usage
<Button>{t('panel.save')}</Button>
```

**Pluralization / ICU MessageFormat**: не нужно в POC-1. Если понадобится — добавить `intl-messageformat` (~10 KB gzipped) на JS-стороне + миграция `.properties` на ICU-синтаксис в проблемных ключах.

**Накладные расходы**: snapshot типично 1-5 KB JSON на бандл, отправляется один раз на панель. Дешевле чем кастомный ResourceBundle-loader в JS.

### 8.5. Developer Experience: layout, build, reuse

Главный вопрос: как **прозрачно** иметь Kotlin + TS в одном модуле, чтобы разработчику было понятно что где лежит и как собирается.

#### 8.5.1. Структура директорий (recommended для production pattern)

```
community/platform/ui.myfeature/
├── intellij.platform.ui.myfeature.iml       # Kotlin module (source of truth)
├── BUILD.bazel
├── src/                                     # Kotlin sources
│   └── com/intellij/myfeature/
│       ├── MyFeaturePanel.kt                # Swing host (ToolWindowFactory)
│       ├── MyFeatureCommands.kt             # Bridge command registry
│       ├── MyFeatureBundle.kt               # i18n entry point
│       └── ...
├── resources/
│   ├── META-INF/plugin.xml
│   ├── messages/MyFeatureBundle.properties
│   └── webview/                             # Built web assets (artefact layer)
│       └── views/
│           └── my-feature/
│               ├── index.html
│               ├── view.js
│               └── styles.css
├── webview-src/                             # TS/TSX sources (co-located с Kotlin)
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── views/
│   │   └── my-feature/
│   │       ├── index.html
│   │       └── src/
│   │           ├── main.tsx
│   │           ├── App.tsx
│   │           ├── bridge/
│   │           │   ├── rpc.ts                # thin client над WebView Interop (`window.__WVI__`)
│   │           │   ├── types.generated.ts    # mirror Kotlin @Serializable (manual or codegen)
│   │           │   └── i18n.ts               # t()
│   │           └── components/ ...
│   └── tests/
│       └── App.test.tsx
└── tests/testResources/...
```

Ключевое:
- `webview-src/` лежит **внутри** Kotlin-модуля, рядом с `src/`. Разработчик видит и Kotlin, и TS в одном дереве.
- `webview-src/views/<view-id>/` — source, `resources/webview/views/<view-id>/` — build output. Vite настроен через общий helper.
- Bazel `resourcegroup` подхватывает `resources/webview/**` как обычный ресурс.

#### 8.5.2. Build: Vite + Bazel

**Вариант A — Pragmatic (рекомендуется для POC-1 и ранней фазы)**:
- Разработчик запускает `bun install && bun run build` в `webview-src/` локально или через IDE run config.
- Built output (`resources/webview/`) генерируется сборкой и не коммитится.
- CI gate: `build_web_assets` собирает output заново; fail если frontend build падает.
- **Pros**: нулевая Bazel-инфраструктура, быстро стартует, debuggable.
- **Cons**: repo size растёт (mitigation: output минимизированный, vendor excluded), noisy commits.

**Вариант B — Bazel-integrated (целевое для POC-2+)**:
- `aspect_rules_ts` + `aspect_rules_js` + custom `vite_bundle` macro.
- Bazel rule эмитит `webview_dist` как hermetic artefact, `pkg_jar` запаковывает.
- Precedent в монорепо: **Jupyter** frontend собран таким образом (Angular + npm + Bazel, `plugins/jupyter/frontend/`).
- **Pros**: нет drift, fully hermetic, no committed build output.
- **Cons**: Bazel-TS integration требует setup (rules, npm_install, node_toolchain); дольше onboarding.

**Рекомендация**: POC-1 — Вариант A. Переключиться на B после того, как паттерн доказан в 2-3 модулях.

#### 8.5.3. Kotlin + TS coexistence в IDE

Что IDE делает автоматически:
- Открывает `webview-src/package.json` → включает Node.js integration и TypeScript.
- `webview-src/tsconfig.json` — TypeScript service picks up settings.
- Autocomplete, navigate-to-definition, refactoring работают **отдельно** в Kotlin и TS.

Что нужно добавить в модуль:

**Run configurations** (в `.idea/runConfigurations/`):
- `MyFeature WebView Build` — `bun run build` в `webview-src/`.
- `MyFeature WebView Dev` — `bun run dev` (Vite dev server на :5173).
- `MyFeature WebView Test` — `vitest run`.

**Dev-server режим**:

```kotlin
// MyFeaturePanel.kt
private val devServerUrl: String? get() =
  Registry.stringValue("ui.myfeature.webview.devServer").takeIf { it.isNotBlank() }

private fun loadContent() {
  val url = devServerUrl
    ?: assetLoader.getEntryUrl("webview/views/my-feature/index.html")  // production: из classpath
  facade.loadUrl(url)
}
```

Разработчик:
1. Запускает `MyFeature WebView Dev` (Vite HMR).
2. В IDE Registry ставит `ui.myfeature.webview.devServer=http://localhost:5173/`.
3. Перезапускает IDE / переоткрывает ToolWindow.
4. Получает hot-reload React/TS прямо в работающей IDE.

Это **ключевая DevX-фича**: edit TS/CSS → сохранение → мгновенное обновление в WebView без перезапуска IDE. Compose с Jewel такого не давал.

#### 8.5.4. Shared UI components library

Для переиспользования одних компонент в нескольких модулях/плагинах — отдельный platform-модуль:

```
community/platform/ui.webview.components/
├── intellij.platform.ui.webview.components.iml
├── src/
│   └── com/intellij/ui/webview/components/
│       ├── ThemeBridge.kt              # public: публикует IJ theme → CSS vars
│       ├── BundledAssetsProvider.kt    # public: предоставляет entry-URL для lib
│       └── ...
├── webview-src/
│   ├── package.json                    # name: "@intellij/ui-components"
│   ├── src/
│   │   ├── index.ts                    # public exports
│   │   ├── Button.tsx
│   │   ├── Dialog.tsx
│   │   ├── Tooltip.tsx
│   │   ├── CodeBlock.tsx
│   │   ├── MarkdownView.tsx
│   │   └── theme/
│   │       ├── tokens.ts               # CSS variables naming
│   │       └── applyTheme.ts           # hook для ThemeBridge events
│   └── dist/                           # bundled ES module
└── resources/
    └── webview/components/
        └── dist/...                    # (в варианте A) committed output
```

**Consumer modules**:
1. В `.iml` добавляется dependency:
   ```xml
   <orderEntry type="module" module-name="intellij.platform.ui.webview.components" />
   ```
2. В `webview-src/package.json` — file link:
   ```json
   {
      "dependencies": { "@intellij/ui-components": "file:../../../ui.webview.components/webview-src" }
   }
   ```
3. В TS:
   ```ts
   import { Button, CodeBlock } from '@intellij/ui-components';
   ```

Vite (у consumer'а) бандлит `@intellij/ui-components` в свой output вместе со своим кодом — tree-shakeable. Это дубликация кода (если 5 feature-модулей используют library — 5 копий в JAR'ах). Для mitigation:

**Long-term (POC-2+): custom `ij-webview://` scheme handler** (уже запланирован в POC docs):
- `ij-webview://components/Button.js` резолвится через classloader модуля `ui.webview.components`.
- Consumer'ский Vite НЕ бандлит lib (external в config); runtime резолвит.
- Одна копия кода, dynamic import.
- Требует scheme handler + shared module registry.

#### 8.5.5. IJ theme bridge (критично для shared components)

Без единого theming contract'а каждый компонент будет переопределять цвета свои. Решение — один canonical CSS vars contract:

```kotlin
// ThemeBridge.kt (в ui.webview.components)
object ThemeBridge {
  fun snapshot(): Map<String, String> {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val ui = UIManager.getDefaults()
    return mapOf(
      "--ij-color-background"   to ui.getColor("Panel.background").toHex(),
      "--ij-color-foreground"   to ui.getColor("Panel.foreground").toHex(),
      "--ij-color-selection"    to scheme.defaultSelectionColor.toHex(),
      "--ij-font-size"          to "${scheme.editorFontSize}px",
      "--ij-font-family"        to scheme.editorFontName,
      "--ij-focus-ring"         to JBUI.CurrentTheme.Focus.focusColor().toHex(),
      // ... curated token set
    )
  }

  fun install(bus: WebViewMessageBus, scope: CoroutineScope, project: Project?) {
    bus.publish("theme/snapshot", snapshot())
    project?.messageBus?.connect(scope.asDisposable())?.subscribe(
      EditorColorsManager.TOPIC, EditorColorsListener { bus.publish("theme/snapshot", snapshot()) }
    )
  }
}
```

JS:

```ts
rpc.on('theme/snapshot', (tokens: Record<string, string>) => {
  const root = document.documentElement;
  for (const [k, v] of Object.entries(tokens)) root.style.setProperty(k, v);
});
```

Все web-компоненты используют `var(--ij-color-*)` — theme switching работает без React-re-render (чистые CSS custom properties).

#### 8.5.6. Type-safe bridge

Три варианта, ordered по зрелости:

| Вариант | Описание | Когда |
|---|---|---|
| **A. Manual TS types** | Разработчик вручную дублирует Kotlin `@Serializable` в `webview-src/views/<view-id>/src/bridge/types.ts`. | POC-1. Быстро, но error-prone при scale. |
| **B. Codegen из Kotlin** | Kotlin `@Serializable` → TS через kxs-ts-gen / jsonschema + quicktype. Output — `types.generated.ts`. CI gate: regen + diff. | POC-2+. Исключает drift. |
| **C. Spec-first (OpenAPI / JSON-Schema)** | Общий spec генерит оба: Kotlin data classes + TS types + JSON-Schema валидация. | Long-term. Самое строгое, но heaviest setup. |

Рекомендация: A → B после первых 2-3 модулей.

#### 8.5.7. Testing story

- **Unit TS**: vitest в `webview-src/tests/`. Запускается через run config и в CI.
- **Visual/snapshot**: Playwright + storybook ИЛИ vitest + @testing-library/react + DOM snapshots. Screenshot diff через git-LFS или dedicated bucket.
- **Bridge integration**: Kotlin JUnit тест с fake WebView-facade (`FakeWebViewMessageBus`) — проверяет корректность message flow без реального WKWebView.
- **E2E**: существующий POC `MacWebViewSmokeTest.kt` как baseline; расширяется на real Safari driver для DOM assertions.

### 8.6. Summary: что надо иметь готовым для proposal-ревью

| Pain-point | Что нужно показать | Статус в POC-0 |
|---|---|---|
| Focus incoming | `requestWebViewFocus()` + `host/focusEntry` IPC | Hooks есть, focusEntry pending |
| Focus outgoing | `host/focusExit` IPC + JS Tab-boundary detector | Pending (POC-1) |
| Tab order | `ComponentsListFocusTraversalPolicy` + единый tab stop | Swing-паттерн known, integration pending |
| Actions | Named command registry + `ide/invokeAction` с allowlist | POC-0 имеет notifications, RPC в POC-1 |
| i18n | `*Bundle.snapshot()` + `i18n/snapshot` publish | Bundle есть, snapshot паттерн не реализован |
| Build | Vite + committed dist (вариант A) | Сейчас plain JS без build — миграция нужна |
| Dev server | Registry flag + `devServerUrl` fallback | Не реализовано, простое добавление |
| Shared lib | `ui.webview.components` модуль + theme bridge | Не существует — зародыш в POC demo |
| Type safety | Manual mirror types → codegen | POC-1 spec явно говорит "no codegen" |
| Testing | vitest + Playwright + Kotlin fake bus | Только smoke-test в POC-0 |

Прогресс-путь: POC-1 закрывает focus exit, RPC, i18n snapshot, dev server, manual types. POC-2 — shared components, codegen, Playwright E2E.

---

## Приложение A. Референсы в коде

### POC (ветка `n500/262/light-webview-poc`)

| Файл | Что показывает |
|---|---|
| `community/platform/ui.webview/README.md` | Обзор POC |
| `community/platform/ui.webview/docs/Lightweight-System-WebView-React-Integration.md` | Архитектура и hypothesis |
| `community/platform/ui.webview/docs/Platform-Wide-Viability-Report.md` | Аргументы про web как рендерер |
| `community/platform/ui.webview/docs/WebView-Kotlin-JSON-RPC-Spec.md` | POC-1 IPC-спецификация |
| `community/platform/ui.webview/docs/poc-0-decisions/01-awt-nsview-embedding.md` | Native child view vs OSR |
| `community/platform/ui.webview/docs/poc-0-decisions/02-edt-main-thread-marshalling.md` | Threading model |
| `community/platform/ui.webview/docs/poc-0-decisions/03-minimal-js-jvm-bridge.md` | JSON-RPC bridge |
| `community/platform/ui.webview/docs/poc-0-decisions/05-focus-keyboard-ime.md` | Focus boundary — open questions |
| `community/platform/ui.webview/docs/poc-0-decisions/07-lifecycle-and-cancellation.md` | CoroutineScope-first |
| `community/platform/ui.webview/docs/poc-0-decisions/09-crash-leak-diagnostics.md` | Crash observer plan (pending) |
| `community/platform/ui.webview/src/com/intellij/ui/webview/internal/mac/MacWebViewFacade.kt` | macOS backend |
| `community/platform/ui.webview/src/com/intellij/ui/webview/internal/mac/WKWebViewBridge.kt` | ObjC/JNA bridge |
| `community/platform/ui.webview/src/com/intellij/ui/webview/interop/WebViewMessageBus.kt` | Message bus |
| `community/platform/ui.webview/demo/` | Kanban + mermaid демо |
| `community/plugins/markdown/core/src/org/intellij/plugins/markdown/ui/preview/webview/MarkdownWebViewHtmlPanel.kt` | Параллельная WebView markdown preview — точка JCEF-консолидации |

### JCEF (для консолидации и compare)

| Файл | Что показывает |
|---|---|
| `community/platform/ui.jcef/jcef/JBCefBrowserBuilder.java:38-48` | **OOP JCEF forcibly disables native rendering** (ключевая находка) |
| `community/platform/ui.jcef/jcef/JBCefApp.java` | `isRemoteEnabled`, `isOffScreenRenderingModeEnabled` |
| `community/platform/ui.jcef/jcef/JBCefBrowserBase.java` | Rendering-mode gating |
| `community/platform/platform-tests/testSrc/com/intellij/ui/jcef/JBCefJSQueryOSRTest.java` | Существующий OSR test setup |
| `JBR-2751` (YouTrack) | Markdown crashes IDE with JCEF enabled on Linux |

---

## Приложение B. Внешние источники

### Process isolation и crash model

- [Process model for WebView2 apps (Microsoft Learn)](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/process-model)
- [Handling process-related events in WebView2](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/process-related-events)
- [`webViewWebContentProcessDidTerminate` (Apple Developer)](https://developer.apple.com/documentation/webkit/wknavigationdelegate/webviewwebcontentprocessdidterminate(_:))
- [Handling Blank WKWebViews (nevermeant.dev)](https://nevermeant.dev/handling-blank-wkwebviews/)
- [WebKit bug 176855 — unable to recover WKWebView after termination](https://bugs.webkit.org/show_bug.cgi?id=176855)
- [Unresponsive web processes in WPE and WebKitGTK (Igalia)](https://blogs.igalia.com/magomez/2021/06/28/unresponsive-web-processes-in-wpe-and-webkitgtk/)

### JCEF / IntelliJ platform

- [Embedded Browser (JCEF) — IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)
- [JCEF: the missing libs problem on Linux — JetBrains Support](https://intellij-support.jetbrains.com/hc/en-us/articles/360016421559-JCEF-the-missing-libs-problem-on-Linux)
- [JetBrains Runtime — GitHub](https://github.com/JetBrains/JetBrainsRuntime)
- [Tests using JCEF on Windows failed with 2025.1 — JetBrains Platform forum](https://platform.jetbrains.com/t/tests-using-jcef-on-windows-failed-with-2025-1/1493)
- [IJPL-184288 — Can't create Windowed browser when OOP enabled (YouTrack)](https://youtrack.jetbrains.com/issue/IJPL-184288)
- [IntelliJ IDEA 2025.1 release blog](https://blog.jetbrains.com/idea/2025/04/intellij-idea-2025-1/)

### Tauri / external precedent

- [Tauri discussion #8524 — WebKit instability on Linux](https://github.com/orgs/tauri-apps/discussions/8524)
- [Tauri issue #13498 — random freezing](https://github.com/tauri-apps/tauri/issues/13498)
- [Tauri issue #11432 — WebView crashes on certain pages (STATUS_ACCESS_VIOLATION)](https://github.com/tauri-apps/tauri/issues/11432)
- [Tauri 2.0 Stable Release blog](https://v2.tauri.app/blog/tauri-20/)

### CEF / Chromium

- [`JBCefBrowserBuilder.java` on GitHub (idea/243 branch)](https://github.com/JetBrains/intellij-community/blob/idea/243.22562.145/platform/ui.jcef/jcef/JBCefBrowserBuilder.java)
- [CEF Forum — Browser overlaps other Swing components (heavyweight/lightweight)](https://www.magpcss.org/ceforum/viewtopic.php?f=17&t=14713)
- [Asciidoctor plugin issue #1575 — OSR disabled exception](https://github.com/asciidoctor/asciidoctor-intellij-plugin/issues/1575)
