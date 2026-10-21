package org.jetbrains.plugins.textmate

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.bundles.TextMateFileNameMatcher
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import java.nio.file.Path

/**
 * The built-in bundles register from `lib/bundles/index.txt`. A bundle loads on the first request of one of its grammars.
 */
class TextMateLazyBundleLoadingTest : BasePlatformTestCase() {
  private val service: TextMateServiceImpl
    get() = TextMateService.getInstance() as TextMateServiceImpl

  override fun setUp() {
    super.setUp()
    service.reloadEnabledBundles()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  fun testMappingComesFromTheIndexWithoutALoad() {
    assertEquals("source.kotlin", service.fileNameMatcherToScopeNameMapping[TextMateFileNameMatcher.Extension("kt")].toString())
    assertTrue(service.hasLanguageForFileName("Main.kt"))
    assertTrue(service.hasLanguageForFileName("Dockerfile"))
    assertFalse(service.hasLanguageForFileName("Main.unknown-extension"))
    assertEmpty(service.loadedBundleNames())
  }

  fun testBundleLoadsOnTheFirstDescriptorRequest() {
    val descriptor = service.getLanguageDescriptorByFileName("Main.kt")
    assertNotNull(descriptor)
    assertEquals("source.kotlin", descriptor!!.rootScopeName.toString())
    assertEquals(setOf("kotlin"), service.loadedBundleNames())

    assertNotNull(service.getLanguageDescriptorByExtension("bat"))
    assertEquals(setOf("kotlin", "bat"), service.loadedBundleNames())
  }

  fun testIncludedBundlesLoadWithTheRequestedOne() {
    val descriptor = service.getLanguageDescriptorByFileName("page.html")
    assertNotNull(descriptor)
    assertEquals("text.html.derivative", descriptor!!.rootScopeName.toString())
    // the HTML grammar includes source.css and source.js
    assertEquals(setOf("html", "css", "javascript"), service.loadedBundleNames())
  }

  fun testPreferencesOfALoadedBundleAreAvailable() {
    assertNotNull(service.getLanguageDescriptorByFileName("Main.kt"))
    assertNotEmpty(service.preferenceRegistry.getPreferences(TextMateScope("source.kotlin", null)))
  }

  fun testProviderWithDeclaredExtensionsLoadsOnTheFirstRequest() {
    val provider = object : TextMateBundleProvider {
      override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        return listOf(TextMateBundleProvider.PluginBundle("turtle", Path.of(TestUtilMultiplatform.getBundleDirectoryPath(TestUtil.TURTLE))))
      }

      override fun getExtensions(): Set<String> = setOf("TTL")
    }
    Disposer.register(testRootDisposable) {
      service.reloadEnabledBundles()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    ExtensionTestUtil.maskExtensions(TextMateBundleProvider.EP_NAME, listOf(provider), testRootDisposable)
    service.reloadEnabledBundles()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertTrue(service.hasLanguageForFileName("graph.ttl"))
    assertTrue(service.hasLanguageForFileName("GRAPH.TTL"))
    assertNull(service.fileNameMatcherToScopeNameMapping[TextMateFileNameMatcher.Extension("ttl")])
    assertEmpty(service.loadedBundleNames())

    val descriptor = service.getLanguageDescriptorByFileName("graph.ttl")
    assertNotNull(descriptor)
    assertEquals("source.turtle", descriptor!!.rootScopeName.toString())
    assertEquals(setOf("turtle"), service.loadedBundleNames())
    assertEquals("source.turtle", service.fileNameMatcherToScopeNameMapping[TextMateFileNameMatcher.Extension("ttl")].toString())
    assertNotNull(service.getLanguageDescriptorByExtension("TTL"))
  }
}
