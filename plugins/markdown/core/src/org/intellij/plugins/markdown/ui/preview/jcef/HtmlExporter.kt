// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Base64
import com.intellij.util.io.HttpRequests
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.File
import java.io.IOException

data class HtmlResourceSavingSettings(val isSaved: Boolean, val resourceDir: String)

class HtmlExporter(htmlSource: String,
                   private val savingSettings: HtmlResourceSavingSettings,
                   private val project: Project,
                   private val targetFile: File) {
  private val document = Jsoup.parse(htmlSource)

  fun export() {
    with(document.head()) {
      getElementsByTag("script").remove()
      getElementsByTag("meta").remove()
      appendInlineStylesContent(select("link[rel=\"stylesheet\"]"))
    }

    val images = document.body().getElementsByTag("img")
    if (savingSettings.isSaved) {
      saveImages(images)
    }
    else {
      inlineImagesContent(images)
    }

    targetFile.writeText(document.html())
  }

  private fun appendInlineStylesContent(styles: Elements) {
    val inlinedStyles = styles.mapNotNull {
      val content = getStyleContent(it) ?: return@mapNotNull null
      Element("style").text(content)
    }
    styles.remove()

    inlinedStyles.forEach {
      if (it.hasText()) {
        document.head().appendChild(it)
      }
    }
  }

  private fun getStyleContent(linkElement: Element): String? {
    val url = linkElement.attr("href") ?: return null

    return try {
      HttpRequests.request(url).readString()
    }
    catch (exception: IOException) {
      val name = File(url).name
      MarkdownNotifier.showWarningNotification(project, MarkdownBundle.message("markdown.export.style.not.found.msg", name))
      null
    }
  }

  private fun inlineImagesContent(images: Elements) {
    images.forEach {
      val imgSrc = getImgUriWithProtocol(it.attr("src"))
      val content = getResource(imgSrc)

      if (content != null && imgSrc.isNotEmpty()) {
        it.attr("src", encodeImage(imgSrc, content))
      }
    }
  }

  private fun encodeImage(url: String, bytes: ByteArray): String {
    val extension = FileUtil.getExtension(url, "png")
    val contentType = if (extension == "svg") "svg+xml" else extension

    return "data:image/$contentType;base64, ${Base64.encode(bytes)}"
  }

  private fun saveImages(images: Elements) {
    images.forEach {
      val imgSrc = it.attr("src")
      val imgUri = getImgUriWithProtocol(imgSrc)
      val content = getResource(imgUri)

      if (content != null && imgSrc.isNotEmpty()) {
        val savedImgFile = getSavedImageFile(savingSettings.resourceDir, imgSrc)
        FileUtil.createIfDoesntExist(savedImgFile)
        savedImgFile.writeBytes(content)

        val relativeImgPath = getRelativeImagePath(savingSettings.resourceDir)
        it.attr("src", FileUtil.join(relativeImgPath, File(imgSrc).name))
      }
    }
  }

  private fun getImgUriWithProtocol(imgSrc: String): String {
    return if (imgSrc.startsWith("file:")) imgSrc
    else File(imgSrc).toURI().toString()
  }

  private fun getResource(url: String): ByteArray? =
    try {
      HttpRequests.request(url).readBytes(null)
    }
    catch (exception: IOException) {
      val name = File(url).name
      MarkdownNotifier.showWarningNotification(project, MarkdownBundle.message("markdown.export.images.not.found.msg", name))
      null
    }

  private fun getSavedImageFile(resDir: String, imgUrl: String) = File(FileUtil.join(resDir, File(imgUrl).name))

  private fun getRelativeImagePath(resDir: String) = FileUtil.getRelativePath(targetFile.parentFile, File(resDir))
}
