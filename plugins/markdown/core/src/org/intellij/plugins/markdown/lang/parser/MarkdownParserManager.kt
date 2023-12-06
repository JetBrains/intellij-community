// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReference

@Service
class MarkdownParserManager: Disposable {
  private class ParsingResult(
    val buffer: CharSequence,
    val tree: ASTNode,
    val bufferHash: Int = buffer.hashCode()
  )

  private val lastParsingResult = AtomicReference<SoftReference<ParsingResult>>()

  @JvmOverloads
  fun parse(buffer: CharSequence, flavour: MarkdownFlavourDescriptor = FLAVOUR): ASTNode {
    val wrappedBuffer = object: BombedCharSequence(buffer) {
      override fun checkCanceled() {
        ProgressManager.checkCanceled()
      }
    }
    return performParsing(wrappedBuffer, flavour)
  }

  private fun performParsing(buffer: CharSequence, flavour: MarkdownFlavourDescriptor = FLAVOUR): ASTNode {
    val info = lastParsingResult.get()?.get()
    if (info != null && info.bufferHash == buffer.hashCode() && info.buffer == buffer) {
      return info.tree
    }
    val parseResult = MarkdownParser(flavour).parse(
      MarkdownElementTypes.MARKDOWN_FILE,
      buffer.toString(),
      parseInlines = false
    )
    lastParsingResult.set(SoftReference(ParsingResult(buffer, parseResult)))
    return parseResult
  }

  override fun dispose() {
    lastParsingResult.set(null)
  }

  companion object {
    @JvmField
    @Deprecated("Use MarkdownFile.getFlavour() instead")
    val FLAVOUR_DESCRIPTION = Key.create<MarkdownFlavourDescriptor>("Markdown.Flavour")

    @JvmField
    val FLAVOUR: MarkdownFlavourDescriptor = MarkdownDefaultFlavour()

    @JvmStatic
    fun getInstance(): MarkdownParserManager {
      return service()
    }

    @JvmStatic
    @JvmOverloads
    fun parseContent(buffer: CharSequence, flavour: MarkdownFlavourDescriptor = FLAVOUR): ASTNode {
      return getInstance().parse(buffer, flavour)
    }
  }
}
