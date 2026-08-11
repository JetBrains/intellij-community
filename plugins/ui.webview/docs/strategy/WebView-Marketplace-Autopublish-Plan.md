# WebView Marketplace Autopublish Plan

Status: ✅ **implemented**. This document records the release-build wiring for publishing the WebView runtime plugin, the Markdown WebView preview plugin, and the WebView demo plugin to JetBrains Marketplace. It is a build/distribution note.

## Current State

All three plugins have Bazel ZIP targets that can be built manually:

- `@community//plugins/ui.webview:webview_plugin_zip`
- `@community//plugins/ui.webview/markdown-preview:markdown_webview_preview_plugin_zip`
- `@community//plugins/ui.webview/demo:platform_ui_webview_demo_plugin_zip`

The runtime ZIP is shaped as `webview/` and includes the WebView runtime jar, the JCEF content-module jar under `lib/modules/`, and Windows native bridge files under `lib/webview-native/win/`. The Markdown preview ZIP is shaped as `markdown-webview-preview/` and includes its plugin jar. The demo ZIP is shaped as `platform-ui-webview-demo/` and includes its plugin jar.

Those Bazel ZIP targets are useful verification/manual artifacts, but they do not by themselves put a plugin into the release auto-upload area used for Marketplace publication.

## Desired Release Path

Use the standard product build path for Marketplace publication:

1. Product build collects compatible non-bundled plugins.
2. `PluginAutoPublishList` reads `build/plugins-autoupload.txt`.
3. Matching plugin ZIPs are written under the product artifacts' `*-plugins/auto-uploading/` directory.
4. The release upload job picks up that `auto-uploading` directory for Marketplace upload.

Compatibility should be defined by plugin descriptors and their dependencies, not by product-code rules in `plugins-autoupload.txt`. Prefer bare module-name entries over `+IU:`/`+IC:` rules unless a real product-specific restriction appears later.

## Required Repository Changes

Keep all three main modules in `build/plugins-autoupload.txt`:

```text
intellij.platform.ui.webview
intellij.markdown.webview.preview
intellij.platform.ui.webview.demo
```

Keep the same main modules in the non-bundled product content for IDEA Ultimate, DataGrip, and CLion packaging test data so product builds collect compatible plugin ZIPs for publishing.

The standard Marketplace ZIP for `intellij.platform.ui.webview` must preserve the current runtime plugin shape:

- directory name: `webview`
- main jar: `com.intellij.platform.ui.webview.jar`
- Windows native bridge files copied from `plugins/ui.webview/lib/webview-native/win` to `lib/webview-native/win`
- JCEF content module left to the existing `<content>` auto-layout so `intellij.platform.ui.webview.jcef` is packaged under `lib/modules/`

The standard Marketplace ZIP for `intellij.markdown.webview.preview` must keep the current artifact names:

- directory name: `markdown-webview-preview`
- main jar: `org.intellij.plugins.markdown.webview.preview.jar`

The standard Marketplace ZIP for `intellij.platform.ui.webview.demo` should use the default plugin layout names:

- directory name: `platform-ui-webview-demo`
- main jar: `platform-ui-webview-demo.jar`

Do not change descriptor IDs or dependencies as part of publication wiring. `allow-bundled-update` is not required for the demo plugin while it remains non-bundled in product layouts.

## Artifact Shape

The expected Marketplace artifacts are versioned ZIPs produced by the product build:

```text
webview-<plugin-version>.zip
markdown-webview-preview-<plugin-version>.zip
platform-ui-webview-demo-<plugin-version>.zip
```

The plugin IDs remain unchanged:

```text
com.intellij.platform.ui.webview
org.intellij.plugins.markdown.webview.preview
com.intellij.platform.ui.webview.demo
```

## Verification

For the existing manual Bazel ZIPs:

```shell
./bazel.cmd build @community//plugins/ui.webview:webview_plugin_zip @community//plugins/ui.webview/markdown-preview:markdown_webview_preview_plugin_zip @community//plugins/ui.webview/demo:platform_ui_webview_demo_plugin_zip
```

For the standard auto-upload allowlist and build-script path:

```shell
./tests.cmd --module intellij.idea.ultimate.build.tests --test com.intellij.idea.ultimate.build.tests.buildScripts.PluginAutoUploadListTest
```

After implementing the build-script wiring, also inspect the product build artifacts and confirm that all three plugin ZIPs are produced under `auto-uploading/`, not only under the general non-bundled plugins directory.

## Assumptions

- Marketplace publication should use the standard release `auto-uploading/` path rather than a separate Bazel upload step.
- Product compatibility should be determined by plugin descriptors and dependency resolution.
- The custom Bazel ZIP targets remain useful for direct verification and local/manual packaging.
- This plan intentionally does not change WebView runtime APIs, Markdown preview APIs, or demo plugin APIs.
