// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DigestUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.lang.parser.CancellableText
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.ui.preview.html.links.IntelliJImageGeneratingProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.math.BigInteger
import java.net.URI

object MarkdownUtil {
  @ApiStatus.Internal
  fun md5(buffer: String?, @NonNls key: String): String {
    val md5 = DigestUtil.md5()
    md5.update(buffer?.toByteArray(Charsets.UTF_8))
    val code = md5.digest(key.toByteArray(Charsets.UTF_8))
    val bi = BigInteger(code).abs()
    return bi.abs().toString(16)
  }

  @JvmOverloads
  fun generateMarkdownHtml(
    file: VirtualFile,
    text: String,
    project: Project?,
    flavour: MarkdownFlavourDescriptor = MarkdownParserManager.FLAVOUR,
  ): String {
    val parsedTree = MarkdownParserManager.createMarkdownParser(flavour)
      .buildMarkdownTreeFromString(CancellableText.of(text))

    val linkMap = LinkMap.buildLinkMap(parsedTree, text)
    val footnoteMap = FootnoteMap.build(parsedTree, text, flavour)
    val map = MarkdownParserManager.FLAVOUR.createHtmlGeneratingProviders(linkMap, null).toMutableMap()
    map[GFMElementTypes.ALERT] = MarkdownAlertGeneratingProvider(map[MarkdownElementTypes.BLOCK_QUOTE]!!)
    map[MarkdownElementTypes.CODE_FENCE] = createCodeFenceProvider(project, file)
    if (project != null) {
      map[MarkdownElementTypes.IMAGE] = IntelliJImageGeneratingProvider(linkMap)
      map[MarkdownElementTypes.PARAGRAPH] = ParagraphGeneratingProvider()
      map[MarkdownElementTypes.CODE_SPAN] = CodeSpanRunnerGeneratingProvider(project, file)
    }
    map[MarkdownElementTypes.FULL_REFERENCE_LINK] = FootnoteReferenceProvider(footnoteMap, map[MarkdownElementTypes.FULL_REFERENCE_LINK]!!)
    map[MarkdownElementTypes.SHORT_REFERENCE_LINK] = FootnoteReferenceProvider(footnoteMap, map[MarkdownElementTypes.SHORT_REFERENCE_LINK]!!)
    map[MarkdownElementTypes.PARAGRAPH] = FootnoteNodeSuppressor(footnoteMap, map[MarkdownElementTypes.PARAGRAPH]!!)
    map[MarkdownElementTypes.CODE_BLOCK] = FootnoteNodeSuppressor(footnoteMap, map[MarkdownElementTypes.CODE_BLOCK]!!)
    map[MarkdownElementTypes.CODE_FENCE] = FootnoteNodeSuppressor(footnoteMap, map[MarkdownElementTypes.CODE_FENCE]!!)
    map.addFlavourProviders(flavour, linkMap)

    val mainHtml = HtmlGenerator(text, parsedTree, map, true).generateHtml()

    val footnoteHtml = footnoteMap.generateFootnoteHtml()
    return if (footnoteHtml.isEmpty()) mainHtml
           else mainHtml.dropLast("</body>".length) + "\n" + footnoteHtml + "\n</body>"
  }

  @ApiStatus.Internal
  fun createCodeFenceProvider(project: Project?, file: VirtualFile?): GeneratingProvider {
    val providers = CodeFenceGeneratingProvider.collectProviders()
    return DefaultCodeFenceGeneratingProvider(providers.toTypedArray(), project, file)
  }
}

/** Adds the providers of [flavour] for the element types that this map does not render. Tokens keep the default rendering. */
internal fun MutableMap<IElementType, GeneratingProvider>.addFlavourProviders(
  flavour: MarkdownFlavourDescriptor,
  linkMap: LinkMap,
  baseURI: URI? = null,
) {
  if (flavour === MarkdownParserManager.FLAVOUR) {
    return
  }
  for ((type, provider) in flavour.createHtmlGeneratingProviders(linkMap, baseURI)) {
    if (type !in this && !(type is MarkdownElementType && type.isToken)) {
      this[type] = provider
    }
  }
}
