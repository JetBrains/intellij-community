// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType
import java.math.BigInteger
import java.util.*

class LiteralConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKLiteralExpression) return recurse(element)
        return try {
            with(element) {
                convertLiteral()
                if (type == LiteralType.TEXT_BLOCK) addTrimIndentCall() else this
            }
        } catch (_: NumberFormatException) {
            createTodoCall(cannotConvertLiteralMessage(element))
        }
    }

    private fun JKLiteralExpression.addTrimIndentCall() = JKQualifiedExpression(
        copyTreeAndDetach(),
        JKCallExpressionImpl(
            symbolProvider.provideMethodSymbol("kotlin.text.trimIndent"),
            expressionType = typeFactory.types.string
        )
    ).withFormattingFrom(this)

    private fun createTodoCall(@NonNls message: String): JKCallExpressionImpl {
        val todoMethodSymbol = symbolProvider.provideMethodSymbol("kotlin.TODO")
        val todoMessageArgument = JKArgumentImpl(JKLiteralExpression("\"$message\"", LiteralType.STRING))

        return JKCallExpressionImpl(todoMethodSymbol, JKArgumentList(todoMessageArgument))
    }

    private fun cannotConvertLiteralMessage(element: JKLiteralExpression): String {
        val literalType = element.type.toString().lowercase(Locale.getDefault())
        val literalValue = element.literal
        return "Could not convert $literalType literal '$literalValue' to Kotlin"
    }

    private fun JKLiteralExpression.convertLiteral() {
        literal = when (type) {
            LiteralType.DOUBLE -> toDoubleLiteral()
            LiteralType.FLOAT -> toFloatLiteral()
            LiteralType.LONG -> toLongLiteral()
            LiteralType.INT -> toIntLiteral()
            LiteralType.CHAR -> convertCharLiteral()
            LiteralType.STRING -> toStringLiteral()
            LiteralType.TEXT_BLOCK -> toRawStringLiteral()
            else -> return
        }
    }

    private fun JKLiteralExpression.toDoubleLiteral(): String {
        var text = literal.cleanFloatAndDoubleLiterals()
        if (text.isHexLiteral()) {
            // Java supports hexadecimal floating point literals, which should be converted to decimal in Kotlin
            text = text.toDouble().toString()
        }
        if (!text.contains(".") && !text.contains("e", ignoreCase = true)) text = "$text."
        if (text.endsWith(".")) text = "${text}0"
        return text
    }

    private fun String.isHexLiteral(): Boolean =
        startsWith("0x", ignoreCase = true)

    private fun JKLiteralExpression.toFloatLiteral(): String {
        var text = literal.cleanFloatAndDoubleLiterals()
        if (text.isHexLiteral()) {
            // Java supports hexadecimal floating point literals, which should be converted to decimal in Kotlin
            text = text.toFloat().toString()
        }
        if (!text.endsWith("f")) text = "${text}f"
        return text
    }

    private fun JKLiteralExpression.toLongLiteral(): String =
        literal
            .cleanIntAndLongLiterals()
            .convertHexLiteral(isLongLiteral = true)
            .convertBinaryLiteral(isLongLiteral = true)
            .convertOctalLiteral(isLongLiteral = true) + "L"

    private fun JKLiteralExpression.toIntLiteral(): String =
        literal
            .cleanIntAndLongLiterals()
            .convertHexLiteral(isLongLiteral = false)
            .convertBinaryLiteral(isLongLiteral = false)
            .convertOctalLiteral(isLongLiteral = false)

    private fun String.convertHexLiteral(isLongLiteral: Boolean): String {
        if (!isHexLiteral()) return this
        val value = BigInteger(drop(2), 16)
        return when {
            isLongLiteral && value.bitLength() > 63 ->
                "-0x${value.toLong().toString(16).substring(1)}"

            !isLongLiteral && value.bitLength() > 31 ->
                "-0x${value.toInt().toString(16).substring(1)}"

            else -> this
        }
    }

    private fun String.convertBinaryLiteral(isLongLiteral: Boolean): String {
        if (!startsWith("0b", ignoreCase = true)) return this
        val value = BigInteger(drop(2), 2)
        return if (isLongLiteral) value.toLong().toString(10) else value.toInt().toString()
    }

    private fun String.convertOctalLiteral(isLongLiteral: Boolean): String {
        if (!startsWith("0") || length == 1 || get(1).lowercaseChar() == 'x') return this
        val value = BigInteger(drop(1), 8)
        return if (isLongLiteral) value.toLong().toString(10) else value.toInt().toString(10)
    }

    private fun String.cleanFloatAndDoubleLiterals() =
        replace("L", "", ignoreCase = true)
            .replace("d", "", ignoreCase = true)
            .replace(".e", "e", ignoreCase = true)
            .replace(".f", "", ignoreCase = true)
            .replace("f", "", ignoreCase = true)
            .replace("_", "")

    private fun String.cleanIntAndLongLiterals() =
        replace("l", "", ignoreCase = true)
            .replace("_", "")

    private fun JKLiteralExpression.convertCharLiteral(): String =
        literal.replace(charOctalEscapeRegex) {
            String.format("\\u%04x", Integer.parseInt(it.groupValues[1], 8))
        }

    private fun JKLiteralExpression.toStringLiteral(): String =
        literal
            .replaceOctalEscapes(format = "%s\\u%04x")
            .replace(dollarRegex, "\\\\$0")
            .replaceFormFeed()

    private fun JKLiteralExpression.toRawStringLiteral(): String {
        // remove implicit newlines that were suppressed with a single backslash at end of line
        literal = literal.replace(implicitNewlineRegex, "$1")

        while (literal.contains("\\n")) {
            // replace escaped newlines with real newlines and leading indenting spaces
            literal = literal.replace(escapedNewlineRegex, "\n$1$2\n$1")
        }

        rawStringSpecialCharSimpleReplacements.forEach { (old: String, new: String) -> literal = literal.replace(old, new) }

        rawStringSpecialCharRegexReplacements.forEach { (pattern: Regex, replacement: String) ->
            literal = literal.replace(pattern) { matchResult ->
                val leadingBackslashes = matchResult.groupValues[1]
                // if the number of leading backslashes is odd, then the next backslash
                // is actually an escaped backslash, not a part of the char escape sequence.
                if (leadingBackslashes.length % 2 == 0) "$leadingBackslashes$replacement" else matchResult.value
            }
        }

        return literal
            .replaceOctalEscapes(format = "%s\${'\\u%04x'}")
            // unescape backslashes
            .replace("\\\\", "\\")
            // add a trailing line break and leading indenting spaces before the closing triple quote
            .replace(closingTripleQuoteRegex, "\n$1$2\n$1\"\"\"")
    }

    private fun String.replaceOctalEscapes(format: String): String =
        replace(stringOctalEscapeRegex) { matchResult ->
            val leadingBackslashes = matchResult.groupValues[1]
            // if the number of leading backslashes is odd, then the backslash in "\123"
            // is actually an escaped backslash, not a part of the octal escape sequence.
            if (leadingBackslashes.length % 2 == 0) {
                String.format(format, leadingBackslashes, Integer.parseInt(matchResult.groupValues[2], 8))
            } else {
                matchResult.value
            }
        }

    private fun String.replaceFormFeed(): String =
        replace(formFeedRegex) { matchResult ->
            val leadingBackslashes = matchResult.groupValues[1]
            // if the number of leading backslashes is odd, then the backslash in "\f"
            // is actually an escaped backslash, not a part of the form feed character.
            if (leadingBackslashes.length % 2 == 0) "$leadingBackslashes\\u000c" else matchResult.value
        }
}

private val rawStringSpecialCharSimpleReplacements: Map<String, String> = mapOf(
    "\$" to "\${'$'}",
    "\\\'" to "'",
    "\\\"" to "\${'\"'}",
)
private val rawStringSpecialCharRegexReplacements: Map<Regex, String> = mapOf(
    """(\\*)\\040""".toRegex() to " ", // escaped (trailing) space
    """(\\*)\\s""".toRegex() to " ", // also escaped (trailing) space
    """(\\*)\\r""".toRegex() to "\${'\\r'}",
    """(\\*)\\t""".toRegex() to "\${'\\t'}",
    """(\\*)\\b""".toRegex() to "\${'\\b'}",
    """(\\*)\\f""".toRegex() to "\${'\\u000c'}"
)
private val dollarRegex = """\$([A-Za-z]+|\{)""".toRegex()
private val formFeedRegex = """(\\*)\\f""".toRegex()
private val implicitNewlineRegex = "([^\\\\])\\\\\n\\s*".toRegex()
private val escapedNewlineRegex = """\n(\s*)(.*)(\\n)""".toRegex()
private val closingTripleQuoteRegex = "\\n(\\s*)(.*)\"\"\"\\Z".toRegex()
private val charOctalEscapeRegex = """\\([0-3]?[0-7]{1,2})""".toRegex()
private val stringOctalEscapeRegex = """(\\*)\\([0-3]?[0-7]{1,2})""".toRegex()
