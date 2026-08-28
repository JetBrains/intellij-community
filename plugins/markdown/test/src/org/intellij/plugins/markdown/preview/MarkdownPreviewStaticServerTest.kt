// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.HttpRequests
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.jetbrains.ide.BuiltInServerManager

class MarkdownPreviewStaticServerTest: LightPlatformTestCase() {
  private class TestResourceProvider: ResourceProvider {
    override fun canProvide(resourceName: String): Boolean {
      return resourceName == TestResourceProvider.resourceName
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource {
      return ResourceProvider.Resource(resourceContent.toByteArray())
    }

    companion object {
      const val resourceName = "test-resource"
      const val resourceContent = "test-resource-content"
    }
  }

  private class PageResourceProvider: ResourceProvider {
    override fun canProvide(resourceName: String): Boolean {
      return resourceName == RESOURCE_NAME
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource {
      return ResourceProvider.Resource("<html></html>".toByteArray(), "text/html", isDocument = true)
    }

    companion object {
      const val RESOURCE_NAME = "test-page.html"
    }
  }

  fun `test preview server returns some path without exceptions`() {
    val provider = TestResourceProvider()
    val path = PreviewStaticServer.getStaticUrl(provider, TestResourceProvider.resourceName)
    assertTrue(path.contains(TestResourceProvider.resourceName))
  }

  fun `test preview server serves resource without exceptions`() {
    val provider = TestResourceProvider()
    withServedResource(provider, TestResourceProvider.resourceName) { url ->
      val content = HttpRequests.request(url).readString()
      assertEquals(TestResourceProvider.resourceContent, content)
    }
  }

  /** An SVG from a document is served here; opening its URL used to run its scripts (IJPL-247809). */
  fun `test served resource is not usable as a document and leaks no referrer`() {
    val provider = TestResourceProvider()
    withServedResource(provider, TestResourceProvider.resourceName) { url ->
      assertEquals("default-src 'none'; sandbox", headerOf(url, "Content-Security-Policy"))
    }
  }

  /** A document declares its own policy in markup; a second one in a header would be enforced separately. */
  fun `test document resource is served without a policy header`() {
    val provider = PageResourceProvider()
    withServedResource(provider, PageResourceProvider.RESOURCE_NAME) { url ->
      assertNull(headerOf(url, "Content-Security-Policy"))
    }
  }

  private fun headerOf(url: String, name: String): String? {
    return HttpRequests.request(url).connect { it.connection.getHeaderField(name) }
  }

  private fun withServedResource(provider: ResourceProvider, resourceName: String, action: (String) -> Unit) {
    BuiltInServerManager.getInstance().waitForStart()
    val disposable = PreviewStaticServer.instance.registerResourceProvider(provider)
    try {
      action(PreviewStaticServer.getStaticUrl(provider, resourceName))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
