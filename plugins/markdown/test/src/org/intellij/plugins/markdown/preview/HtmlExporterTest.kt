// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.intellij.plugins.markdown.preview

import com.intellij.markdown.jcef.preview.HtmlExporter
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.fileActions.export.MarkdownHtmlExportResult
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.jetbrains.ide.BuiltInServerManager
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.io.path.readBytes
import kotlin.io.path.readText

@TestApplication
class HtmlExporterTest {
  private val project by projectFixture()
  private val tempDirectory by tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `project image with an encoded space is included in exported HTML`() {
    val imageUrl = serveImage("images/2026-06-15 16.17.35.jpg")

    val result = HtmlExporter("<img src=\"$imageUrl\">", project, imagesSrcDir = null).export()

    val imageSrc = Jsoup.parse(result.html).getElementsByTag("img").single().attr("src")
    assertThat(imageSrc).startsWith("data:image/jpg;base64,")
    assertThat(Base64.getDecoder().decode(imageSrc.removePrefix("data:image/jpg;base64,"))).containsExactly(1, 2, 3)
    assertThat(result.images).isEmpty()
  }

  @Test
  fun `content security policy allows embedded images`() {
    val source = "<html><head><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'\"></head><body></body></html>"

    val result = HtmlExporter(source, project, imagesSrcDir = null).export()

    val csp = Jsoup.parse(result.html).head().selectFirst("meta[http-equiv=Content-Security-Policy]")!!.attr("content")
    val imgSrc = csp.lines().single { it.startsWith("img-src") }
    assertThat(imgSrc.removeSuffix(";").split(" ")).contains("data:")
  }

  @Test
  fun `saved image is written to the resource directory and referenced by a relative path`() {
    val imageUrl = serveImage("images/logo.png")
    val targetFile = tempDirectory.resolve("README.html").toFile()
    val resourceDir = tempDirectory.resolve("README").toString()
    val imagesSrcDir = MarkdownHtmlExportResult.getImagesSrcDir(targetFile, resourceDir)

    HtmlExporter("<img src=\"$imageUrl\">", project, imagesSrcDir).export().writeTo(targetFile, resourceDir)

    val exportedImage = Jsoup.parse(targetFile.toPath().readText()).getElementsByTag("img").single()
    assertThat(exportedImage.attr("src")).isEqualTo("README/logo.png")
    assertThat(tempDirectory.resolve("README/logo.png").readBytes()).containsExactly(1, 2, 3)
  }

  @Test
  fun `exported document declares UTF-8 and keeps non-ASCII text`() {
    val targetFile = tempDirectory.resolve("README.html").toFile()
    val source = "<html><head><meta charset=\"windows-1251\"><meta name=\"viewport\" content=\"width=device-width\"></head><body><p>Привет, 日本語</p></body></html>"

    HtmlExporter(source, project, imagesSrcDir = null).export().writeTo(targetFile, tempDirectory.toString())

    val document = Jsoup.parse(targetFile.readText(Charsets.UTF_8))
    val metas = document.head().getElementsByTag("meta")
    assertThat(metas.first()!!.attr("charset")).isEqualTo("UTF-8")
    assertThat(metas.filter { it.hasAttr("charset") }).hasSize(1)
    assertThat(document.body().text()).isEqualTo("Привет, 日本語")
  }

  private fun serveImage(imagePath: String): String {
    val resourceProvider = object : ResourceProvider {
      override fun canProvide(resourceName: String) = resourceName == imagePath

      override fun loadResource(resourceName: String) = ResourceProvider.Resource(byteArrayOf(1, 2, 3))
    }
    Disposer.register(disposable, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))
    BuiltInServerManager.getInstance().waitForStart()
    return PreviewStaticServer.getStaticUrl(resourceProvider, imagePath)
  }
}
