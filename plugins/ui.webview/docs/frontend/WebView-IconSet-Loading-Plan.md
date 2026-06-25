# WebView IconSet Loading Plan

Status: ⬜ **DESIGN ONLY**. No Kotlin icon-set registration API, no TypeScript `IconSet` helper, and no WebView icon asset route exist yet. This doc is the v1 spec when work starts.

## Goal

Let WebView TypeScript code use IntelliJ Platform icons by classpath resource path without knowing anything about Swing `Icon`, `IconLoader`, plugin classloaders, or the current IDE theme implementation.

The author-facing API should stay simple:

```tsx
<img
  src={AllIcons.src("expui/breakpoints/breakpoint.svg")}
  width={16}
  height={16}
  alt=""
/>
```

The string passed from TypeScript is the resource path inside the registered icon-set classloader. It is not a repository path. For example, the checkout file:

```text
community/platform/icons/src/expui/breakpoints/breakpoint.svg
```

is referenced from WebView code as:

```text
expui/breakpoints/breakpoint.svg
```

## Public Shape

An icon set is a named WebView asset namespace backed by one fixed JVM classloader.

Kotlin registers the icon sets available to the page:

```kotlin
import com.intellij.icons.AllIcons
import com.intellij.ui.webview.WebViewAssetRoot
import com.intellij.ui.webview.WebViewIconSet

private val assetRoot = WebViewAssetRoot
  .forView("my-view")
  .withIconSets(
    WebViewIconSet.of("AllIcons", AllIcons::class.java),
    WebViewIconSet.of("MyPluginIcons", MyPluginIcons::class.java),
  )
```

The Kotlin API is intentionally about the owner class, not about individual icons:

```kotlin
class WebViewIconSet private constructor(
  val id: String,
  internal val classLoader: ClassLoader,
) {
  companion object {
    fun of(id: String, owner: Class<*>): WebViewIconSet
  }
}

fun WebViewAssetRoot.withIconSets(vararg iconSets: WebViewIconSet): WebViewAssetRoot
```

TypeScript defines stable icon-set objects by id:

```ts
import { IconSet } from "@jetbrains/intellij-webview"

export const AllIcons = IconSet.define("AllIcons")
export const MyPluginIcons = IconSet.define("MyPluginIcons")
```

Feature code then references only resource paths:

```tsx
import { AllIcons } from "./iconSets"

export function BreakpointIcon(): JSX.Element {
  return (
    <img
      src={AllIcons.src("expui/breakpoints/breakpoint.svg")}
      width={16}
      height={16}
      alt=""
    />
  )
}
```

No generated `AllIcons.Actions.Copy` constants and no reflective `com.intellij.icons.AllIcons` member lookup are part of v1.

## Theme Resolution

`IconSet` resolves the light/dark flavor on the TypeScript side from the current WebView theme state. Callers do not pass a theme flag.

```text
AllIcons.src("expui/breakpoints/breakpoint.svg")
  -> ./__ij-icons/AllIcons/light/expui/breakpoints/breakpoint.svg

AllIcons.src("expui/breakpoints/breakpoint.svg")
  -> ./__ij-icons/AllIcons/dark/expui/breakpoints/breakpoint.svg
```

The URL includes the flavor so normal browser image caching cannot accidentally reuse a light icon after switching to a dark theme, or the opposite. Components that render icons should compute `src(...)` during render from the current theme-backed `IconSet` state; framework adapters can expose a hook or signal later if needed.

The JVM provider still owns resource fallback for the requested flavor:

```text
dark + expui/breakpoints/breakpoint.svg
  1. expui/breakpoints/breakpoint_dark.svg
  2. expui/breakpoints/breakpoint.svg
```

Light requests use the original path directly. Missing dark variants fall back to the light resource rather than forcing every icon to have a dark copy.

## Asset Route

Use a readable WebView-local URL shape:

```text
./__ij-icons/<icon-set-id>/<light|dark>/<resource-path>
```

Examples:

```text
./__ij-icons/AllIcons/light/expui/breakpoints/breakpoint.svg
./__ij-icons/AllIcons/dark/expui/breakpoints/breakpoint.svg
./__ij-icons/MyPluginIcons/light/icons/myAction.svg
```

`<resource-path>` stays a path, not an opaque encoded blob. Unsafe characters are percent-escaped per path segment, but `/` remains the separator.

The asset provider resolves requests by `(iconSetId, flavor, resourcePath)`:

```kotlin
private fun resolveIconBytes(
  iconSetId: String,
  flavor: WebViewIconFlavor,
  resourcePath: String,
): WebViewAssetResponse? {
  val iconSet = registeredIconSets[iconSetId] ?: return null
  val resolvedPath = when (flavor) {
    WebViewIconFlavor.LIGHT -> resourcePath
    WebViewIconFlavor.DARK -> darkPath(resourcePath)
  }

  return iconSet.classLoader.getResourceAsStream(resolvedPath)?.toAssetResponse()
    ?: iconSet.classLoader.getResourceAsStream(resourcePath)?.toAssetResponse()
}
```

The real implementation should avoid fallback duplication for light requests, set correct content type (`image/svg+xml` or `image/png`), and keep caching headers consistent with existing WebView static assets.

## Validation

TypeScript should reject invalid icon-set ids early when `IconSet.define(...)` is called. Kotlin should reject duplicate ids during `WebViewAssetRoot` construction.

Resource paths must be treated as classpath resource names, not file-system paths:

- no leading `/`;
- no URI scheme;
- no `..` segment;
- no empty segment;
- supported extensions only: `.svg` and `.png` for v1.

Malformed requests should return 403. Unknown icon-set ids and missing resources should return 404.

## Scope

V1 serves raw SVG/PNG resources from the registered classloader. It does not render Swing `Icon` instances to PNG, does not inspect `AllIcons` fields, and does not generate TypeScript constants.

Out of scope for v1:

- HiDPI scale buckets and `srcSet`;
- animated icon state;
- icon metadata extraction from Java classes;
- plugin-wide automatic registration;
- external HTTP serving outside the existing WebView asset layer.

These can be added later without changing the call site shape, because `AllIcons.src(path)` already hides URL generation from feature code.

## Test Plan

Kotlin tests:

- registered `AllIcons` resolves `expui/breakpoints/breakpoint.svg` through the `AllIcons` classloader;
- dark requests prefer `*_dark.svg` when it exists and fall back to the original path otherwise;
- two registered icon sets with the same resource path resolve through their own owner classloaders;
- unknown icon-set id returns 404;
- leading slash, URI scheme, `..`, empty segment, and unsupported extension return 403.

TypeScript tests:

- `IconSet.define("AllIcons").src("expui/breakpoints/breakpoint.svg")` includes the current flavor and readable resource path;
- light/dark theme changes produce different URLs;
- unsafe path characters are escaped without collapsing `/` separators;
- invalid icon-set ids and invalid resource paths fail before creating a URL.

Suggested verification commands after implementation:

```shell
./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WebViewIconSetAssetProviderTest
```

```shell
bun test
bun run typecheck
```

Run the TypeScript commands from the owning `webview-src` package that contains the `@jetbrains/intellij-webview` source/tests.
