package org.intellij.plugins.markdown.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.WhitespacesBinders
import com.intellij.psi.ParsingDiagnostics
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IStubFileElementType
import org.intellij.plugins.markdown.lang.MarkdownLazyElementType.obtainFlavour
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.lang.parser.PsiBuilderFillingVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

/**
 * The root element type for Markdown files.
 *
 * The Markdown parser produces its own AST, while IntelliJ PSI is built through [PsiBuilder]. The parsed Markdown AST is
 * therefore created once here, then passed to [MarkdownToplevelLexer] to provide the builder with token boundaries and to
 * [PsiBuilderFillingVisitor] to create the hierarchical IntelliJ AST. The lexer is backed by the already parsed tree and
 * does not invoke the Markdown parser again.
 *
 * [PsiBuilder] is still required because it creates the IntelliJ AST/PSI nodes, handles whitespace binders, and preserves
 * the platform's lazy-parse contract.
 */
open class MarkdownFileElementType: IStubFileElementType<PsiFileStub<MarkdownFile>>(
  "MarkdownFile",
  MarkdownLanguage.INSTANCE
) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val flavour = obtainFlavour(psi.containingFile)

    val startTime = System.nanoTime()
    val parsedTree = MarkdownParserManager.parseContent(chameleon.chars, flavour)
    val lexer = MarkdownToplevelLexer(flavour, parsedTree)
    val builder = PsiBuilderFactory.getInstance()
      .createBuilder(psi.project, chameleon, lexer, MarkdownLanguage.INSTANCE, chameleon.chars)
    ParsingDiagnostics.registerParse(builder, MarkdownLanguage.INSTANCE, System.nanoTime() - startTime)

    val rootMarker = builder.mark()
    rootMarker.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER)
    PsiBuilderFillingVisitor(builder, true).visitNode(parsedTree)
    assert(builder.eof())
    rootMarker.done(this)
    return builder.treeBuilt.firstChildNode
  }
}
