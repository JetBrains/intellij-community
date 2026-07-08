package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.util.prevLeafs
import java.util.EnumSet
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol.Companion.isDeclaration
import org.intellij.plugins.markdown.util.isFootnoteLabelText

internal class MarkdownLinkLabelCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType !in SUPPORTED_COMPLETION_TYPES) return
    val position = parameters.position
    val openingBracket = findLabelCompletionStart(position) ?: return
    val document = position.containingFile.fileDocument
    val isDefinition = isAtLineStart(document, openingBracket)
    val prefix = document.immutableCharSequence.subSequence(openingBracket + 1, parameters.offset).toString()
    val names = collectLinkLabelNames(parameters.originalFile, isDefinition)
    result.withPrefixMatcher(prefix).addAllElements(names.map(::linkLabelLookupElement))
  }
}

private val CODE_ELEMENT_TYPES: TokenSet = TokenSet.create(
  MarkdownElementTypes.CODE_SPAN, MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK,
  MarkdownElementTypes.INLINE_MATH, MarkdownElementTypes.BLOCK_MATH,
)

private val SUPPORTED_COMPLETION_TYPES: Set<CompletionType> = EnumSet.of(CompletionType.BASIC, CompletionType.SMART)

private fun findLabelCompletionStart(position: PsiElement): Int? {
  for (element in position.parents(withSelf = false)) {
    when {
      element.elementType in CODE_ELEMENT_TYPES -> return null
      element is MarkdownLinkLabel -> return element.textRange.startOffset
      element is MarkdownLinkText -> return null
    }
  }
  for (leaf in position.prevLeafs) {
    if (leaf.textContains('\n')) break
    when (leaf.elementType) {
      MarkdownTokenTypes.LBRACKET -> return leaf.textRange.startOffset
      MarkdownTokenTypes.RBRACKET -> return null
    }
  }
  return null
}

private fun isAtLineStart(document: Document, openingBracket: Int): Boolean {
  val lineStart = document.getLineStartOffset(document.getLineNumber(openingBracket))
  return document.immutableCharSequence.subSequence(lineStart, openingBracket).isBlank()
}

private fun linkLabelLookupElement(name: String): LookupElement = LookupElementBuilder.create(name).withIcon(MarkdownIcons.EditorActions.Link)

private fun collectLinkLabelNames(file: PsiFile, isDefinition: Boolean): List<String> {
  val defined = LinkedHashSet<String>()
  val referenced = LinkedHashSet<String>()
  for (label in PsiTreeUtil.findChildrenOfType(file, MarkdownLinkLabel::class.java)) {
    if (isFootnoteLabelText(label.text)) continue
    val name = label.labelText
    if (name.isEmpty()) continue
    if (label.isDeclaration) defined += name else referenced += name
  }
  return (if (isDefinition) referenced - defined else defined).toList()
}