// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetProviderResult
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.WEBVIEW_ASSET_HTTPS_HOST
import com.intellij.ui.webview.impl.WebViewAssetResolver
import com.intellij.ui.webview.impl.resolveWebViewAssetUrl
import com.intellij.ui.webview.impl.webViewAssetHttpsUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal class WebViewAssetResolverTest {
  @Test
  fun resolvesDirectoryRootThroughVirtualUrl(@TempDir tempDir: Path) {
    Files.writeString(tempDir.resolve("index.html"), /*language=HTML*/ "<html><body>disk-root</body></html>")
    Files.writeString(tempDir.resolve("view.js"), /*language=JavaScript*/ "console.log('ok')")

    val resolver = WebViewAssetResolver(WebViewAssetRoot.fromDirectory(tempDir))
    val url = webViewAssetHttpsUrl(WebViewAssetPath.indexHtml())
    assertTrue(url.startsWith("https://$WEBVIEW_ASSET_HTTPS_HOST/"))
    val response = resolveWebViewAssetUrl(url, resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertEquals("text/html; charset=utf-8", response.contentType)
    assertEquals(/*language=HTML*/ "<html><body>disk-root</body></html>", response.bytes.toString(StandardCharsets.UTF_8))

    val jsResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("view.js")), resolver)
    assertNotNull(jsResponse)
    assertEquals("text/javascript; charset=utf-8", jsResponse!!.contentType)
  }

  @Test
  fun rejectsEncodedTraversalThroughVirtualUrl(@TempDir tempDir: Path) {
    Files.writeString(tempDir.resolve("index.html"), "ok")
    val resolver = WebViewAssetResolver(WebViewAssetRoot.fromDirectory(tempDir))
    val response = resolveWebViewAssetUrl("https://$WEBVIEW_ASSET_HTTPS_HOST/%2e%2e/secret.txt", resolver)
    assertNotNull(response)

    assertEquals(403, response!!.statusCode)
  }

  @Test
  fun returnsNotFoundForMissingDirectoryAsset(@TempDir tempDir: Path) {
    val resolver = WebViewAssetResolver(WebViewAssetRoot.fromDirectory(tempDir))
    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("missing.css")), resolver)
    assertNotNull(response)

    assertEquals(404, response!!.statusCode)
  }

  @Test
  fun resolvesVirtualUrlAgainstCurrentRoot(@TempDir tempDir: Path) {
    val firstRoot = Files.createDirectory(tempDir.resolve("first"))
    val secondRoot = Files.createDirectory(tempDir.resolve("second"))
    Files.writeString(firstRoot.resolve("index.html"), "first")
    Files.writeString(secondRoot.resolve("index.html"), "second")

    val firstResolver = WebViewAssetResolver(WebViewAssetRoot.fromDirectory(firstRoot))
    val secondResolver = WebViewAssetResolver(WebViewAssetRoot.fromDirectory(secondRoot))
    val url = webViewAssetHttpsUrl(WebViewAssetPath.indexHtml())

    val response = resolveWebViewAssetUrl(url, secondResolver)
    assertNotNull(response)
    assertEquals("second", response!!.bytes.toString(StandardCharsets.UTF_8))

    val activeResponse = resolveWebViewAssetUrl(url, firstResolver)
    assertNotNull(activeResponse)
    assertEquals("first", activeResponse!!.bytes.toString(StandardCharsets.UTF_8))
  }

  @Test
  fun resolvesClasspathRoot() {
    val resolver = WebViewAssetResolver(
      WebViewAssetRoot.fromClasspath(WebViewAssetResolverTest::class.java, WebViewAssetPath.of("webview/views/sample-panel")),
    )
    val indexResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.indexHtml()), resolver)
    assertNotNull(indexResponse)

    val indexHtml = indexResponse!!.bytes.toString(StandardCharsets.UTF_8)
    val bridgeScriptIndex = indexHtml.indexOf("/__webview/wvi-bridge.js")
    val platformFeaturesScriptIndex = indexHtml.indexOf("/__webview/wvi-platform-features.js")
    val viewScriptIndex = indexHtml.indexOf("./view.js")
    assertTrue(bridgeScriptIndex >= 0)
    assertTrue(platformFeaturesScriptIndex >= 0)
    assertTrue(viewScriptIndex >= 0)
    assertTrue(bridgeScriptIndex < platformFeaturesScriptIndex)
    assertTrue(platformFeaturesScriptIndex < viewScriptIndex)

    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("view.js")), resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    val viewScript = response.bytes.toString(StandardCharsets.UTF_8)
    assertTrue(viewScript.contains("React runtime is unavailable"))
    assertFalse(viewScript.contains("installWebViewFocusInterop"))
  }

  @Test
  fun resolvesCommonRuntimeAssetForAnyRoot(@TempDir tempDir: Path) {
    val resolver = WebViewAssetResolver(WebViewAssetRoot.fromDirectory(tempDir))
    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__webview/wvi-bridge.js")), resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    val script = response.bytes.toString(StandardCharsets.UTF_8)
    assertTrue(script.contains("window.__WVI__"))
    assertTrue(script.contains("hostApi"))
    assertTrue(script.contains("notification"))
    assertFalse(script.contains("__wvi-ij-themes"))
    assertFalse(script.contains("webview.theme"))

    val featuresResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__webview/wvi-platform-features.js")), resolver)
    assertNotNull(featuresResponse)

    assertEquals(200, featuresResponse!!.statusCode)
    val featuresScript = featuresResponse.bytes.toString(StandardCharsets.UTF_8)
    assertTrue(featuresScript.contains("__wvi-ij-themes"))
    assertTrue(featuresScript.contains("webview.theme"))
    assertTrue(featuresScript.contains("pointerdown"))
  }

  @Test
  fun scopedProviderWinsOverSameNamedRootAsset(@TempDir tempDir: Path) {
    val namespace = Files.createDirectories(tempDir.resolve("__markdown-preview-resource"))
    Files.writeString(namespace.resolve("image.txt"), "root")
    val resolver = WebViewAssetResolver(
      WebViewAssetRoot.fromDirectory(tempDir).withScopedAssetProvider(WebViewAssetPath.of("__markdown-preview-resource")) { path ->
        WebViewAssetProviderResult.Content.of("text/plain; charset=utf-8", "scoped:${path.path}".toByteArray(StandardCharsets.UTF_8))
      },
    )

    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__markdown-preview-resource/image.txt")), resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertEquals("scoped:image.txt", response.bytes.toString(StandardCharsets.UTF_8))
  }

  @Test
  fun missingScopedProviderAssetDoesNotFallBackToRootAsset(@TempDir tempDir: Path) {
    val namespace = Files.createDirectories(tempDir.resolve("__markdown-preview-resource"))
    Files.writeString(namespace.resolve("image.txt"), "root")
    val resolver = WebViewAssetResolver(
      WebViewAssetRoot.fromDirectory(tempDir).withScopedAssetProvider(WebViewAssetPath.of("__markdown-preview-resource")) { null },
    )

    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__markdown-preview-resource/image.txt")), resolver)
    assertNotNull(response)

    assertEquals(404, response!!.statusCode)
  }

  @Test
  fun resolvesExplicitDevSourceRoot(@TempDir tempDir: Path) = withDevSourceFallbackEnabled {
    Files.writeString(tempDir.resolve("bridge.js"), /*language=JavaScript*/ "window.devRoot = true")

    val resolver = WebViewAssetResolver(
      WebViewAssetRoot.fromClasspath(WebViewAssetResolverTest::class.java, WebViewAssetPath.of("webview/views/sample-panel"), devSourceRoot = tempDir),
    )
    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("bridge.js")), resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertTrue(response.bytes.toString(StandardCharsets.UTF_8).contains("window.devRoot"))
  }

  @Test
  fun fallsBackToClasspathWhenDevSourceEntryIsMissing(@TempDir tempDir: Path) = withDevSourceFallbackEnabled {
    val resolver = WebViewAssetResolver(
      WebViewAssetRoot.fromClasspath(WebViewAssetResolverTest::class.java, WebViewAssetPath.of("webview/views/sample-panel"), devSourceRoot = tempDir),
    )
    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("view.js")), resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertTrue(response.bytes.toString(StandardCharsets.UTF_8).contains("React runtime is unavailable"))
  }

  private fun withDevSourceFallbackEnabled(action: () -> Unit) {
    val property = "ide.webview.assets.use.source.dir"
    val oldValue = System.getProperty(property)
    System.setProperty(property, "true")
    try {
      action()
    }
    finally {
      if (oldValue == null) {
        System.clearProperty(property)
      }
      else {
        System.setProperty(property, oldValue)
      }
    }
  }
}
