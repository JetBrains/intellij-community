package com.intellij.toml.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

private val COMMENT_BUILDER = TextContentBuilder.FromPsi.removingIndents(" \t").removingLineSuffixes(" \t")

class TomlTextExtractor : TextExtractor() {

    override fun buildTextContent(element: PsiElement, allowedDomains: Set<TextDomain>): TextContent? {
        return when {
            TextDomain.LITERALS in allowedDomains && element is TomlLiteral && element.kind is TomlLiteralKind.String -> {
                TextContentBuilder.FromPsi.build(element, TextDomain.LITERALS)
            }
            TextDomain.COMMENTS in allowedDomains && element is PsiComment -> {
                // Allows extracting single text from sequence of line comments
                val siblings = getNotSoDistantSimilarSiblings(element) { it.elementType == element.elementType }
                TextContent.joinWithWhitespace('\n', siblings.mapNotNull { COMMENT_BUILDER.build(it, TextDomain.COMMENTS) })
            }
            else -> null
        }
    }
}
