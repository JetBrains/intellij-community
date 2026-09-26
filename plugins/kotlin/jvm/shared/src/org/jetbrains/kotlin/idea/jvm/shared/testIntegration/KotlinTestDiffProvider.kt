// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.shared.testIntegration

import com.intellij.execution.testframework.JvmTestDiffProvider
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Applies the test diff to a raw string literal that the test trims with `trimIndent()` or `trimMargin()`.
 */
internal class KotlinTestDiffProvider : JvmTestDiffProvider() {
    override fun prepareContent(element: PsiElement, actual: String): String? {
        val trim = element.literalTrim() ?: return super.prepareContent(element, actual)
        return trim.untrim(ElementManipulators.getValueText(element), actual, element.lineIndent())
    }

    /** Returns the whitespace at the start of the line that holds the start of this element. */
    private fun PsiElement.lineIndent(): String {
        val text = containingFile.viewProvider.contents
        val offset = textRange.startOffset
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        return text.subSequence(lineStart, offset).takeWhile { it == ' ' || it == '\t' }.toString()
    }

    override fun unwrapExpected(expression: PsiElement): PsiElement {
        val qualified = expression as? KtQualifiedExpression ?: return expression
        return if (qualified.trim() != null) qualified.receiverExpression else expression
    }

    /**
     * Returns null for a trimmed literal whose text does not start with a line break, because [prepareContent] cannot write to it.
     * The text of a regular string literal holds escapes instead of line breaks.
     */
    override fun comparedValue(literal: PsiElement, value: String): String? {
        val trim = literal.literalTrim() ?: return value
        return if (LiteralTrim.startsWithLineBreak(ElementManipulators.getValueText(literal))) trim.trim(value) else null
    }

    override fun getExpectedValue(element: PsiElement): String {
        val value = super.getExpectedValue(element)
        return comparedValue(element, value) ?: value
    }

    /**
     * Kotlin generates `access$foo` when an inner class calls a private or protected member of the outer class.
     * It generates `foo$default` when a call omits an argument that has a default value.
     * The frame of such a bridge has only the line of the declaration.
     */
    override fun isSyntheticBridge(methodName: String?): Boolean =
        methodName != null && (methodName.startsWith("access$") || methodName.endsWith("\$default"))

    /**
     * Returns the trim when this literal is the receiver of a `trimIndent()` or a `trimMargin()` call.
     */
    private fun PsiElement.literalTrim(): LiteralTrim? {
        val qualified = parent as? KtQualifiedExpression ?: return null
        return if (qualified.receiverExpression == this) qualified.trim() else null
    }

    /**
     * Returns the trim when this expression is a `trimIndent()` or a `trimMargin()` call.
     *
     * The margin prefix of `trimMargin()` must be a string literal without a template entry.
     */
    private fun KtQualifiedExpression.trim(): LiteralTrim? {
        val call = selectorExpression as? KtCallExpression ?: return null
        if (call.lambdaArguments.isNotEmpty()) return null
        val arguments = call.valueArguments
        return when ((call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()) {
            "trimIndent" -> LiteralTrim.Indent.takeIf { arguments.isEmpty() }
            "trimMargin" -> when (arguments.size) {
                0 -> LiteralTrim.Margin("|")
                1 -> arguments.single().getArgumentExpression()?.plainStringValue()?.takeIf { it.isNotBlank() }?.let { LiteralTrim.Margin(it) }
                else -> null
            }
            else -> null
        }
    }

    private fun KtExpression.plainStringValue(): String? {
        val template = this as? KtStringTemplateExpression ?: return null
        return buildString {
            for (entry in template.entries) {
                when (entry) {
                    is KtLiteralStringTemplateEntry -> append(entry.text)
                    is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
                    else -> return null
                }
            }
        }
    }
}

/** A Kotlin call that removes the indent of a raw string literal: `trimIndent()` or `trimMargin()`. */
private sealed interface LiteralTrim {
    fun trim(text: String): String

    /**
     * Returns the text for a literal with the shape of [raw] whose trimmed value is [trimmed].
     *
     * Returns null when [raw] does not start with a blank line, or when no text with this shape trims to [trimmed].
     * [closingIndent] is the indent of a closing line that the text gets when the literal has none and needs one.
     */
    fun untrim(raw: String, trimmed: String, closingIndent: String): String?

    companion object {
        fun startsWithLineBreak(raw: String): Boolean {
            val lineBreak = raw.indexOf('\n')
            return lineBreak >= 0 && raw.substring(0, lineBreak).isBlank()
        }
    }

    /** The text keeps the indent and the blank lines of the literal. */
    object Indent : LiteralTrim {
        override fun trim(text: String): String = text.trimIndent()

        override fun untrim(raw: String, trimmed: String, closingIndent: String): String? {
            if (!startsWithLineBreak(raw)) return null
            val lines = raw.split('\n')
            val indentWidth = lines.filter { it.isNotBlank() }.minOfOrNull { line -> line.indexOfFirst { !it.isWhitespace() } } ?: return null
            val indent = lines.first { it.isNotBlank() }.take(indentWidth)
            if (lines.any { it.isNotBlank() && !it.startsWith(indent) }) return null
            val hasTrailingBlank = lines.last().isBlank()
            val blankLine = lines.subList(1, if (hasTrailingBlank) lines.size - 1 else lines.size)
                .filter { it.isBlank() }
                .distinct()
                .singleOrNull()
            val text = buildString {
                append(lines.first())
                for (line in trimmed.split('\n')) {
                    append('\n')
                    append(blankLine?.takeIf { it.drop(indentWidth) == line } ?: if (line.isEmpty()) "" else indent + line)
                }
                if (hasTrailingBlank) {
                    append('\n')
                    append(lines.last())
                }
                else if (trimmed.substringAfterLast('\n').isBlank()) {
                    // trimIndent drops a blank last line, so the text keeps it with a closing line.
                    append('\n')
                    append(closingIndent)
                }
            }
            return text.takeIf { trim(it) == trimmed }
        }
    }

    /**
     * The text keeps the margin of the literal, which is the whitespace in front of [prefix].
     * Every line gets the prefix, because `trimMargin` keeps a line without the prefix as it is.
     */
    class Margin(private val prefix: String) : LiteralTrim {
        override fun trim(text: String): String = text.trimMargin(prefix)

        override fun untrim(raw: String, trimmed: String, closingIndent: String): String? {
            if (!startsWithLineBreak(raw)) return null
            val lines = raw.split('\n')
            val margin = lines.firstOrNull { it.trimStart().startsWith(prefix) }?.takeWhile { it.isWhitespace() } ?: return null
            val text = buildString {
                append(lines.first())
                for (line in trimmed.split('\n')) {
                    append('\n')
                    append(margin)
                    append(prefix)
                    append(line)
                }
                if (lines.last().isBlank()) {
                    append('\n')
                    append(lines.last())
                }
            }
            return text.takeIf { trim(it) == trimmed }
        }
    }
}
