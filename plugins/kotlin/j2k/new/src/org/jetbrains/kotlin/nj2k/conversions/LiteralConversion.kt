// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKLiteralExpression.LiteralType
import java.math.BigInteger
import java.util.*

class LiteralConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
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

    private fun JKLiteralExpression.toDoubleLiteral(): String =
        literal.cleanFloatAndDoubleLiterals().let { text ->
            if (!text.contains(".") && !text.contains("e", true))
                "$text."
            else text
        }.let { text ->
            if (text.endsWith(".")) "${text}0" else text
        }

    private fun JKLiteralExpression.toFloatLiteral(): String =
        literal.cleanFloatAndDoubleLiterals().let { text ->
            if (!text.endsWith("f")) "${text}f"
            else text
        }

    private fun JKLiteralExpression.toStringLiteral(): String =
        literal
            .replaceOctalEscapes(format = "%s\\u%04x")
            .replace(backslashDollarRegex, "\\\\$0")
            .replace("\\f", "\\u000c")

    private fun JKLiteralExpression.toRawStringLiteral(): String {
        // remove implicit newlines that were suppressed with a single backslash at end of line
        literal = literal.replace(implicitNewlineRegex, "$1")
        while (literal.contains("\\n")) {
            // replace escaped newlines with real newlines and leading indenting spaces
            literal = literal.replace(escapedNewlineRegex, "\n$1$2\n$1")
        }
        rawStringSpecialCharReplacements.forEach { (old, new) -> literal = literal.replace(old, new) }
        return literal
            .replaceOctalEscapes(format = "%s\${'\\u%04x'}")
            // unescape backslashes
            .replace("\\\\", "\\")
            // add a trailing line break and leading indenting spaces before the closing triple quote
            .replace(closingTripleQuoteRegex, "\n$1$2\n$1\"\"\"")
    }

    private fun JKLiteralExpression.convertCharLiteral(): String =
        literal.replace(charOctalEscapeRegex) {
            String.format("\\u%04x", Integer.parseInt(it.groupValues[1], 8))
        }

    private fun JKLiteralExpression.toIntLiteral(): String =
        literal
            .cleanIntAndLongLiterals()
            .convertHexLiteral(isLongLiteral = false)
            .convertBinaryLiteral(isLongLiteral = false)
            .convertOctalLiteral(isLongLiteral = false)

    private fun JKLiteralExpression.toLongLiteral(): String =
        literal
            .cleanIntAndLongLiterals()
            .convertHexLiteral(isLongLiteral = true)
            .convertBinaryLiteral(isLongLiteral = true)
            .convertOctalLiteral(isLongLiteral = true) + "L"

    private fun String.convertHexLiteral(isLongLiteral: Boolean): String {
        if (!startsWith("0x", ignoreCase = true)) return this
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
}

private fun String.replaceOctalEscapes(format: String): String =
    replace(stringOctalEscapeRegex) { matchResult ->
        val leadingBackslashes = matchResult.groupValues[1]
        if (leadingBackslashes.length % 2 == 0)
            String.format(format, leadingBackslashes, Integer.parseInt(matchResult.groupValues[2], 8))
        else matchResult.value
    }

private val rawStringSpecialCharReplacements: Map<String, String> = mapOf(
    "\$" to "\${'$'}",
    "\\040" to " ", // escaped (trailing) space
    "\\s" to " ", // also escaped (trailing) space
    "\\\'" to "'",
    "\\\"" to "\${'\"'}",
    "\\r" to "\${'\\r'}",
    "\\t" to "\${'\\t'}",
    "\\b" to "\${'\\b'}",
    "\\f" to "\${'\\u000c'}"
)

private val backslashDollarRegex = """\$([A-Za-z]+|\{)""".toRegex()
private val implicitNewlineRegex = "([^\\\\])\\\\\n\\s*".toRegex()
private val escapedNewlineRegex = """\n(\s*)(.*)(\\n)""".toRegex()
private val closingTripleQuoteRegex = "\\n(\\s*)(.*)\"\"\"\\Z".toRegex()
private val charOctalEscapeRegex = """\\([0-3]?[0-7]{1,2})""".toRegex()
private val stringOctalEscapeRegex = """(\\*)\\([0-3]?[0-7]{1,2})""".toRegex()
