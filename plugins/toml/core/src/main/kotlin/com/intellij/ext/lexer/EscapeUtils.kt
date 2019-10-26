/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package com.intellij.ext.lexer

import com.intellij.psi.StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
import com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
import com.intellij.psi.tree.IElementType

fun esc(test: Boolean): IElementType = if (test) VALID_STRING_ESCAPE_TOKEN else INVALID_CHARACTER_ESCAPE_TOKEN

/**
 * Determines if the char is a whitespace character.
 */
fun Char.isWhitespaceChar(): Boolean = equals(' ') || equals('\r') || equals('\n') || equals('\t')
