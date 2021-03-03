// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.io.DigestUtil
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.io.File
import java.nio.file.Paths

internal class MermaidCodeGeneratingProviderExtension(
  collector: MarkdownCodeFencePluginCacheCollector? = null
) : MarkdownCodeFenceCacheableProvider(collector),
    MarkdownJCEFPreviewExtension,
    MarkdownExtensionWithExternalFiles,
    ResourceProvider
{
  override val scripts: List<String> = listOf(
    MAIN_SCRIPT_FILENAME,
    THEME_DEFINITION_FILENAME,
    "mermaid/bootstrap.js",
  )

  override val styles: List<String> = listOf("mermaid/mermaid.css")

  override val events: Map<String, (String) -> Unit> =
    mapOf("storeMermaidFile" to this::storeFileEvent)

  override fun isApplicable(language: String) = isEnabled && isAvailable && language == "mermaid"

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val hash = MarkdownUtil.md5(raw, "") + determineTheme()
    val key = getUniqueFile("mermaid", hash, "svg").toFile()
    return if (key.exists()) {
      "<img src=\"${key.toURI()}\"/>"
    } else createRawContentElement(hash, raw)
  }

  fun store(key: String, content: ByteArray) {
    val actualKey = getUniqueFile("mermaid", key, "svg").toFile()
    FileUtil.createParentDirs(actualKey)
    actualKey.outputStream().buffered().use {
      it.write(content)
    }
    collector?.addAliveCachedFile(this, actualKey)
  }

  override fun onLAFChanged() = Unit

  override val displayName: String =
    MarkdownBundle.message("markdown.extensions.mermaid.display.name")

  override val id: String = "MermaidLanguageExtension"

  override val description: String = MarkdownBundle.message("markdown.extensions.mermaid.description")

  override val downloadLink: String = DOWNLOAD_URL

  override val downloadFilename: String = "mermaid.js"

  private val actualFile
    get() = Paths.get(directory.toString(), "mermaid", downloadFilename).toFile()

  private fun isDistributionChecksumValid(): Boolean {
    val got = StringUtil.toHexString(DigestUtil.md5().digest(actualFile.readBytes()))
    return got == CHECKSUM
  }

  override val isAvailable: Boolean
    get() = actualFile.exists() && isDistributionChecksumValid()

  override fun afterDownload(): Boolean {
    val sourceFile = File(directory, downloadFilename)
    sourceFile.copyTo(actualFile, overwrite = true)
    return sourceFile.delete()
  }

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts || resourceName in styles
  }

  private fun determineTheme(): String {
    val registryValue = Registry.stringValue("markdown.mermaid.theme")
    if (registryValue == "follow-ide") {
      val scheme = EditorColorsManager.getInstance().globalScheme
      return if (ColorUtil.isDark(scheme.defaultBackground)) "dark" else "default"
    }
    return registryValue
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return when (resourceName) {
      MAIN_SCRIPT_FILENAME -> ResourceProvider.loadExternalResource(File(directory, resourceName))
      THEME_DEFINITION_FILENAME -> ResourceProvider.Resource("window.mermaidTheme = '${determineTheme()}';".toByteArray())
      else -> ResourceProvider.loadInternalResource(this::class.java, resourceName)
    }
  }

  override val resourceProvider: ResourceProvider = this

  private fun escapeContent(content: String): String {
    return content.replace("<", "&lt;").replace(">", "&gt;")
  }

  private fun createRawContentElement(hash: String, content: String): String {
    return "<div class=\"mermaid\" cache-id=\"$hash\" id=\"$hash\">${escapeContent(content)}</div>"
  }

  private fun storeFileEvent(data: String) {
    if (data.isEmpty()) {
      return
    }
    val key = data.substring(0, data.indexOf(';'))
    val content = data.substring(data.indexOf(';') + 1)
    store(key, content.toByteArray())
  }

  companion object {
    private const val MAIN_SCRIPT_FILENAME = "mermaid/mermaid.js"
    private const val THEME_DEFINITION_FILENAME = "mermaid/themeDefinition.js"
    private const val DOWNLOAD_URL = "https://unpkg.com/mermaid@8.9.1/dist/mermaid.js"
    private const val CHECKSUM = "352791299c7f42ee02e774da58bead4a"
  }
}
