// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DigestUtil
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.ui.preview.html.links.IntelliJImageGeneratingProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.File
import java.math.BigInteger
import java.util.*

object MarkdownUtil {
  @ApiStatus.Internal
  fun md5(buffer: String?, @NonNls key: String): String {
    val md5 = DigestUtil.md5()
    Objects.requireNonNull(md5).update(buffer?.toByteArray(Charsets.UTF_8))
    val code = md5.digest(key.toByteArray(Charsets.UTF_8))
    val bi = BigInteger(code).abs()
    return bi.abs().toString(16)
  }

  fun generateMarkdownHtml(file: VirtualFile, text: String, project: Project?): String {
    val parent = file.parent
    val baseUri = if (parent != null) File(parent.path).toURI() else null

    val parsedTree = MarkdownParser(MarkdownParserManager.FLAVOUR).buildMarkdownTreeFromString(text)
    val cacheCollector = MarkdownCodeFencePluginCacheCollector(file)

    val linkMap = LinkMap.buildLinkMap(parsedTree, text)
    val map = MarkdownParserManager.FLAVOUR.createHtmlGeneratingProviders(linkMap, baseUri).toMutableMap()
    map[MarkdownElementTypes.CODE_FENCE] = createCodeFenceProvider(project, file, cacheCollector)
    if (project != null) {
      map[MarkdownElementTypes.IMAGE] = IntelliJImageGeneratingProvider(linkMap)
      map[MarkdownElementTypes.PARAGRAPH] = ParagraphGeneratingProvider()
      map[MarkdownElementTypes.CODE_SPAN] = CodeSpanRunnerGeneratingProvider(project, file)
    }

    val html = HtmlGenerator(text, parsedTree, map, true).generateHtml()

    MarkdownCodeFenceHtmlCache.getInstance().registerCacheProvider(cacheCollector)

    return html
  }

  @ApiStatus.Internal
  fun createCodeFenceProvider(
    project: Project?,
    file: VirtualFile?,
    collector: MarkdownCodeFencePluginCacheCollector
  ): GeneratingProvider {
    val providers = CodeFenceGeneratingProvider.collectProviders()
    for (provider in providers.asSequence().filterIsInstance<MarkdownCodeFenceCacheableProvider>()) {
      provider.collector = collector
    }
    return DefaultCodeFenceGeneratingProvider(providers.toTypedArray(), project, file)
  }
}
