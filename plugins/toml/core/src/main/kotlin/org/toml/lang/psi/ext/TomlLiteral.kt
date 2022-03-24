/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi.ext

import com.intellij.lang.ASTNode
import org.toml.lang.psi.LiteralOffsets
import org.toml.lang.lexer.unescapeToml
import org.toml.lang.psi.TOML_LITERALS
import org.toml.lang.psi.TOML_STRING_LITERALS
import org.toml.lang.psi.TomlElementTypes.*
import org.toml.lang.psi.TomlLiteral

val TomlLiteral.kind: TomlLiteralKind?
    get() {
        val child = node.findChildByType(TOML_LITERALS) ?: return null
        return TomlLiteralKind.fromAstNode(child) ?: error("Unknown literal: $child (`$text`)")
    }

sealed class TomlLiteralKind(val node: ASTNode) {
    class Boolean(node: ASTNode) : TomlLiteralKind(node)
    class Number(node: ASTNode) : TomlLiteralKind(node)
    class DateTime(node: ASTNode) : TomlLiteralKind(node)
    class String(node: ASTNode) : TomlLiteralKind(node) {
        val value: kotlin.String?
            get() {
                return offsets.value?.substring(node.text)?.unescapeToml(node.elementType)
            }

        val offsets: LiteralOffsets by lazy { offsetsForTomlText(node) }
    }

    companion object {
        fun fromAstNode(node: ASTNode): TomlLiteralKind? {
            return when (node.elementType) {
                BOOLEAN -> Boolean(node)
                NUMBER -> Number(node)
                DATE_TIME -> DateTime(node)
                in TOML_STRING_LITERALS -> String(node)
                else -> null
            }
        }
    }
}

fun offsetsForTomlText(node: ASTNode): LiteralOffsets {
    val (quote, needEscape) = when (node.elementType) {
        BASIC_STRING -> "\"" to true
        MULTILINE_BASIC_STRING -> "\"\"\""  to true
        LITERAL_STRING -> "'"  to false
        MULTILINE_LITERAL_STRING -> "'''" to false
        else -> error("Unexpected element type: `${node.elementType}` for `${node.text}`")
    }

    val openDelimEnd = doLocate(node, 0) { if (it.startsWith(quote)) quote.length else 0 }
    val valueEnd = doLocate(node, openDelimEnd) { text ->
        var escape = false
        for ((i, ch) in text.withIndex()) {
            when {
                escape -> escape = false
                needEscape && ch == '\\' -> escape = true
                text.startsWith(quote, i) -> return@doLocate i
            }
        }
        text.length
    }
    val closeDelimEnd = doLocate(node, valueEnd) { if (it.startsWith(quote)) quote.length else 0 }
    return LiteralOffsets.fromEndOffsets(0, openDelimEnd, valueEnd, closeDelimEnd, 0)
}

private inline fun doLocate(node: ASTNode, start: Int, locator: (CharSequence) -> Int): Int =
    if (start >= node.textLength) start else start + locator(node.chars.subSequence(start, node.textLength))
