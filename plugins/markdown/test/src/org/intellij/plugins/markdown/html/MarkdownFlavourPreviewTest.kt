// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.html

import com.intellij.idea.TestFor
import com.intellij.markdown.jcef.preview.HtmlSourceTextPreprocessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.LanguageFileViewProviders
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ThreeState
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownFileViewProviderFactory
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultFlavour
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultMarkerProcessor
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourProvider
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.lang.parser.blocks.AdmonitionMarkerProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

@TestApplication
@TestFor(issues = ["IJPL-245574"])
internal class MarkdownFlavourPreviewTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val project by projectFixture()

  @Test
  fun `the preview uses the flavour of the file`(): Unit = timeoutRunBlocking {
    useFlavourForFile(InlineFlavour(MarkedWordParser))

    val html = previewHtml("$MARKED_WORD\n")

    assertTrue(html.contains(markedHtml(MARKED_WORD)), html)
  }

  @Test
  fun `the preview uses the flavour of the project provider`(): Unit = timeoutRunBlocking {
    val provider = object : MarkdownFlavourProvider {
      override fun provideFlavour(project: Project): MarkdownFlavourDescriptor = InlineFlavour(MarkedWordParser)
    }
    ExtensionTestUtil.maskExtensions(MarkdownFlavourProvider.extensionPoint, listOf(provider), disposable)

    val html = previewHtml("$MARKED_WORD\n")

    assertTrue(html.contains(markedHtml(MARKED_WORD)), html)
  }

  @Test
  fun `a footnote body uses the flavour of the file`(): Unit = timeoutRunBlocking {
    useFlavourForFile(InlineFlavour(MarkedWordParser))

    val html = previewHtml("Text[^1]\n\n[^1]: $MARKED_WORD\n")

    val footnotes = html.substringAfter("<div class=\"footnotes\">", missingDelimiterValue = "")
    assertTrue(footnotes.contains(markedHtml(MARKED_WORD)), html)
  }

  @Test
  fun `the preview uses a flavour that marks each text token`(): Unit = timeoutRunBlocking {
    useFlavourForFile(InlineFlavour(EachTextParser))

    val html = previewHtml("hello\n")

    assertTrue(html.contains(markedHtml("hello")), html)
  }

  @Test
  fun `the preview uses a custom block of the flavour`(): Unit = timeoutRunBlocking {
    useFlavourForFile(BlockFlavour())

    val html = previewHtml("$FENCE\ncustom content\n$FENCE\n\nafter\n")

    assertTrue(html.contains("<span class=\"marked\">$FENCE\ncustom content\n$FENCE"), html)
    assertTrue(html.contains(">after</span>"), html)
  }

  @Test
  fun `the preview uses the default flavour for the plain-text PSI of a large file`(): Unit = timeoutRunBlocking {
    useFlavourForFile(InlineFlavour(MarkedWordParser))
    LanguageFileViewProviders.INSTANCE.addExplicitExtension(MarkdownLanguage.INSTANCE, MarkdownFileViewProviderFactory(), disposable)
    val file = object : LightVirtualFile("flavour.md", MarkdownFileType.INSTANCE, "$MARKED_WORD\n") {
      override fun isTooLargeForIntelligence(): ThreeState = ThreeState.YES
    }

    val html = previewHtml(file)

    assertFalse(html.contains(markedHtml(MARKED_WORD)), html)
    assertTrue(html.contains(MARKED_WORD), html)
  }

  @Test
  fun `the preview uses a flavour provider for a built-in type that it does not render`(): Unit = timeoutRunBlocking {
    val text = "!!! note\n    body\n"
    val flavour = ProvidersFlavour(listOf(AdmonitionMarkerProvider.ADMONITION))

    val html = generateHtml(text, flavour)

    assertTrue(html.contains("<span class=\"marked\">!!! note"), html)
  }

  @Test
  fun `the preview ignores flavour providers for rendered types and tokens`(): Unit = timeoutRunBlocking {
    val text = "# Header\n\nSome text\n\n    code block\n\n[link]\n\n[link]: https://example.com\n"
    val flavour = ProvidersFlavour(listOf(
      MarkdownElementTypes.ATX_1,
      MarkdownElementTypes.PARAGRAPH,
      MarkdownElementTypes.CODE_BLOCK,
      MarkdownElementTypes.SHORT_REFERENCE_LINK,
      MarkdownTokenTypes.TEXT,
      MarkdownTokenTypes.EOL,
    ))

    val html = generateHtml(text, flavour)

    assertFalse(html.contains("class=\"marked\""), html)
    assertEquals(generateHtml(text, MarkdownParserManager.FLAVOUR), html)
  }

  private fun useFlavourForFile(flavour: MarkdownFlavourDescriptor) {
    val provider = object : MarkdownFlavourProvider {
      override fun provideFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor = flavour
    }
    ExtensionTestUtil.maskExtensions(MarkdownFlavourProvider.extensionPoint, listOf(provider), disposable)
  }

  private suspend fun previewHtml(text: String): String = previewHtml(LightVirtualFile("flavour.md", MarkdownFileType.INSTANCE, text))

  private suspend fun previewHtml(file: VirtualFile): String {
    return readAction {
      val document = FileDocumentManager.getInstance().getDocument(file)!!
      HtmlSourceTextPreprocessor().preprocessText(project, document, file)
    }
  }

  private suspend fun generateHtml(text: String, flavour: MarkdownFlavourDescriptor): String {
    val file = LightVirtualFile("flavour.md", MarkdownFileType.INSTANCE, text)
    return readAction { MarkdownUtil.generateMarkdownHtml(file, text, project, flavour) }
  }

  private class InlineFlavour(private val parser: SequentialParser) : MarkdownDefaultFlavour() {
    private val defaultParserManager = super.sequentialParserManager

    override val sequentialParserManager: SequentialParserManager = object : SequentialParserManager() {
      override fun getParserSequence(): List<SequentialParser> = listOf(parser) + defaultParserManager.getParserSequence()
    }

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      return super.createHtmlGeneratingProviders(linkMap, baseURI) + (MARKED to MarkedProvider)
    }
  }

  private class BlockFlavour : MarkdownDefaultFlavour() {
    override val markerProcessorFactory: MarkerProcessorFactory = object : MarkerProcessorFactory {
      override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> {
        return object : MarkdownDefaultMarkerProcessor(productionHolder, GFMConstraints.BASE) {
          override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> {
            return listOf(FencedBlockProvider) + super.getMarkerBlockProviders()
          }
        }
      }
    }

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      return super.createHtmlGeneratingProviders(linkMap, baseURI) + (MARKED_BLOCK to MarkedProvider)
    }
  }

  private class ProvidersFlavour(private val types: List<IElementType>) : MarkdownDefaultFlavour() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      return super.createHtmlGeneratingProviders(linkMap, baseURI) + types.associateWith { MarkedProvider }
    }
  }

  /** Marks each [MARKED_WORD] and does not pass it on. */
  private object MarkedWordParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
      val result = SequentialParser.ParsingResultBuilder()
      val delegate = RangesListBuilder()
      var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)
      while (iterator.type != null) {
        if (iterator.type == MarkdownTokenTypes.TEXT && tokens.originalText.subSequence(iterator.start, iterator.end) == MARKED_WORD) {
          result.withNode(SequentialParser.Node(iterator.index..iterator.index + 1, MARKED))
        }
        else {
          delegate.put(iterator.index)
        }
        iterator = iterator.advance()
      }
      return result.withFurtherProcessing(delegate.get())
    }
  }

  /** Marks each text token and passes every token on. */
  private object EachTextParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
      val result = SequentialParser.ParsingResultBuilder()
      val delegate = RangesListBuilder()
      var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)
      while (iterator.type != null) {
        if (iterator.type == MarkdownTokenTypes.TEXT) {
          result.withNode(SequentialParser.Node(iterator.index..iterator.index + 1, MARKED))
        }
        delegate.put(iterator.index)
        iterator = iterator.advance()
      }
      return result.withFurtherProcessing(delegate.get())
    }
  }

  /** Opens a [FencedBlock] at a [FENCE] line. */
  private object FencedBlockProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
    override fun createMarkerBlocks(
      pos: LookaheadText.Position,
      productionHolder: ProductionHolder,
      stateInfo: MarkerProcessor.StateInfo,
    ): List<MarkerBlock> {
      if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, stateInfo.currentConstraints)) return emptyList()
      if (pos.currentLineFromPosition.toString() != FENCE) return emptyList()
      return listOf(FencedBlock(stateInfo.currentConstraints, productionHolder))
    }

    override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean = false
  }

  /** Takes the lines up to and including the closing [FENCE] line. */
  private class FencedBlock(
    constraints: MarkdownConstraints,
    productionHolder: ProductionHolder,
  ) : MarkerBlockImpl(constraints, productionHolder.mark()) {
    private var closed = false

    override fun allowsSubBlocks(): Boolean = false

    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = pos.offsetInCurrentLine == -1

    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset

    override fun doProcessToken(pos: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
      if (closed) return MarkerBlock.ProcessingResult.DEFAULT
      if (pos.currentLine.toString() == FENCE) closed = true
      return MarkerBlock.ProcessingResult.CANCEL
    }

    override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE

    override fun getDefaultNodeType(): IElementType = MARKED_BLOCK
  }

  private object MarkedProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
      visitor.consumeHtml(markedHtml(node.getTextInNode(text).toString()))
    }
  }

  private companion object {
    const val MARKED_WORD = "marked"
    const val FENCE = "%%%"
    val MARKED: IElementType = MarkdownElementType("TEST_MARKED")
    val MARKED_BLOCK: IElementType = MarkdownElementType("TEST_MARKED_BLOCK")

    fun markedHtml(text: String): String = "<span class=\"marked\">$text</span>"
  }
}
