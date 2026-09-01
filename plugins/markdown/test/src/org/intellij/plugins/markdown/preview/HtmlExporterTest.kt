// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.intellij.plugins.markdown.preview

import com.intellij.markdown.jcef.preview.HtmlExporter
import com.intellij.markdown.jcef.preview.HtmlResourceSavingSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.jetbrains.ide.BuiltInServerManager
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.io.path.readText

@TestApplication
class HtmlExporterTest {
  private val project by projectFixture()
  private val tempDirectory by tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `project image with an encoded space is included in exported HTML`() {
    val imagePath = "images/2026-06-15 16.17.35.jpg"
    val resourceProvider = object : ResourceProvider {
      override fun canProvide(resourceName: String) = resourceName == imagePath

      override fun loadResource(resourceName: String) = ResourceProvider.Resource(byteArrayOf(1, 2, 3))
    }
    Disposer.register(disposable, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))
    BuiltInServerManager.getInstance().waitForStart()
    val imageUrl = PreviewStaticServer.getStaticUrl(resourceProvider, imagePath)
    val targetFile = tempDirectory.resolve("README.html").toFile()

    HtmlExporter(
      "<img src=\"$imageUrl\">",
      HtmlResourceSavingSettings(isSaved = false, resourceDir = ""),
      project,
      targetFile,
    ).export()

    val exportedImage = Jsoup.parse(targetFile.toPath().readText()).getElementsByTag("img").single()
    assertThat(exportedImage.attr("src")).startsWith("data:image/jpg;base64,")
  }
}
