// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.icons.AllIcons
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewIconSet
import com.intellij.ui.webview.impl.WEBVIEW_ASSET_HTTPS_HOST
import com.intellij.ui.webview.impl.WebViewAssetResolver
import com.intellij.ui.webview.impl.resolveWebViewAssetUrl
import com.intellij.ui.webview.impl.webViewAssetHttpsUrl
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal class WebViewIconSetAssetProviderTest {
  @Test
  fun allIconsResolvesLightResource() {
    val response = resolveAllIconsBreakpoint("light")
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertEquals("image/svg+xml", response.contentType)
    assertArrayEquals(classpathResourceBytes("expui/breakpoints/breakpoint.svg"), response.bytes)
  }

  @Test
  fun allIconsDarkRequestPrefersDarkResource() {
    val response = resolveAllIconsBreakpoint("dark")
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertEquals("image/svg+xml", response.contentType)
    assertArrayEquals(classpathResourceBytes("expui/breakpoints/breakpoint_dark.svg"), response.bytes)
    assertNotEquals(
      classpathResourceBytes("expui/breakpoints/breakpoint.svg").toString(StandardCharsets.UTF_8),
      response.bytes.toString(StandardCharsets.UTF_8),
    )
  }

  @Test
  fun darkRequestFallsBackToOriginalResource() {
    val bytes = "light-only".toByteArray(StandardCharsets.UTF_8)
    val resolver = resolverWithIconSets(WebViewIconSet.of("TestIcons", ownerClass(mapOf("icons/lightOnly.svg" to bytes))))

    val response = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__ij-icons/TestIcons/dark/icons/lightOnly.svg")), resolver)
    assertNotNull(response)

    assertEquals(200, response!!.statusCode)
    assertArrayEquals(bytes, response.bytes)
  }

  @Test
  fun sameResourcePathResolvesThroughRegisteredIconSetClassLoader() {
    val firstBytes = "first".toByteArray(StandardCharsets.UTF_8)
    val secondBytes = "second".toByteArray(StandardCharsets.UTF_8)
    val resolver = resolverWithIconSets(
      WebViewIconSet.of("FirstIcons", ownerClass(mapOf("icons/shared.svg" to firstBytes))),
      WebViewIconSet.of("SecondIcons", ownerClass(mapOf("icons/shared.svg" to secondBytes))),
    )

    val firstResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__ij-icons/FirstIcons/light/icons/shared.svg")), resolver)
    val secondResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__ij-icons/SecondIcons/light/icons/shared.svg")), resolver)
    assertNotNull(firstResponse)
    assertNotNull(secondResponse)

    assertArrayEquals(firstBytes, firstResponse!!.bytes)
    assertArrayEquals(secondBytes, secondResponse!!.bytes)
  }

  @Test
  fun rejectsDuplicateIconSetIds() {
    assertThrows<IllegalArgumentException> {
      WebViewAssetRoot.fromDirectory(tempAssetRoot()).withIconSets(
        WebViewIconSet.of("DuplicateIcons", ownerClass(emptyMap())),
        WebViewIconSet.of("DuplicateIcons", ownerClass(emptyMap())),
      )
    }
  }

  @Test
  fun invalidIconRequestsReturnForbidden() {
    val resolver = resolverWithIconSets(WebViewIconSet.of("AllIcons", AllIcons::class.java))
    val invalidUrls = listOf(
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons",
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons/AllIcons/light//expui/breakpoints/breakpoint.svg",
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons/AllIcons/light/http:icon.svg",
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons/AllIcons/light/expui/../breakpoint.svg",
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons/AllIcons/light/expui//breakpoint.svg",
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons/AllIcons/contrast/expui/breakpoints/breakpoint.svg",
      "https://$WEBVIEW_ASSET_HTTPS_HOST/__ij-icons/AllIcons/light/expui/breakpoints/breakpoint.gif",
    )

    for (url in invalidUrls) {
      val response = resolveWebViewAssetUrl(url, resolver)
      assertNotNull(response)
      assertEquals(403, response!!.statusCode, url)
    }
  }

  @Test
  fun unknownIconSetAndMissingResourceReturnNotFound() {
    val resolver = resolverWithIconSets(WebViewIconSet.of("AllIcons", AllIcons::class.java))

    val unknownSetResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__ij-icons/UnknownIcons/light/expui/breakpoints/breakpoint.svg")), resolver)
    assertNotNull(unknownSetResponse)
    assertEquals(404, unknownSetResponse!!.statusCode)

    val missingResourceResponse = resolveWebViewAssetUrl(webViewAssetHttpsUrl(WebViewAssetPath.of("__ij-icons/AllIcons/light/expui/breakpoints/missing.svg")), resolver)
    assertNotNull(missingResourceResponse)
    assertEquals(404, missingResourceResponse!!.statusCode)
  }

  private fun resolveAllIconsBreakpoint(flavor: String) = resolveWebViewAssetUrl(
    webViewAssetHttpsUrl(WebViewAssetPath.of("__ij-icons/AllIcons/$flavor/expui/breakpoints/breakpoint.svg")),
    resolverWithIconSets(WebViewIconSet.of("AllIcons", AllIcons::class.java)),
  )

  private fun resolverWithIconSets(vararg iconSets: WebViewIconSet): WebViewAssetResolver {
    return WebViewAssetResolver(WebViewAssetRoot.fromDirectory(tempAssetRoot()).withIconSets(*iconSets))
  }

  private fun tempAssetRoot(): Path = Path.of(System.getProperty("java.io.tmpdir"))

  private fun ownerClass(resources: Map<String, ByteArray>): Class<*> {
    val classLoader = ResourceClassLoader(resources)
    return Proxy.newProxyInstance(classLoader, arrayOf(Runnable::class.java)) { _, _, _ -> null }.javaClass
  }

  private fun classpathResourceBytes(path: String): ByteArray {
    return requireNotNull(AllIcons::class.java.classLoader.getResourceAsStream(path)) { "Missing test resource: $path" }.use { it.readBytes() }
  }

  private class ResourceClassLoader(
    private val resources: Map<String, ByteArray>,
  ) : ClassLoader(WebViewIconSetAssetProviderTest::class.java.classLoader) {
    override fun getResourceAsStream(name: String): InputStream? {
      return resources[name]?.let { ByteArrayInputStream(it) } ?: super.getResourceAsStream(name)
    }
  }
}
