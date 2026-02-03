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

  fun `test preview server returns some path without exceptions`() {
    val provider = TestResourceProvider()
    val path = PreviewStaticServer.getStaticUrl(provider, TestResourceProvider.resourceName)
    assertTrue(path.contains(TestResourceProvider.resourceName))
  }

  fun `test preview server serves resource without exceptions`() {
    BuiltInServerManager.getInstance().waitForStart()
    val provider = TestResourceProvider()
    val disposable = PreviewStaticServer.instance.registerResourceProvider(provider)
    try {
      val path = PreviewStaticServer.getStaticUrl(provider, TestResourceProvider.resourceName)
      val content = HttpRequests.request(path).readString()
      assertEquals(TestResourceProvider.resourceContent, content)
    } finally {
      Disposer.dispose(disposable)
    }
  }
}
