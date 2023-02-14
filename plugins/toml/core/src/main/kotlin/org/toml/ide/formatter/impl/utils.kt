/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import org.toml.lang.psi.TomlElementTypes.L_BRACKET
import org.toml.lang.psi.TomlElementTypes.R_BRACKET

val ARRAY_DELIMITERS = TokenSet.create(L_BRACKET, R_BRACKET)

fun ASTNode?.isWhitespaceOrEmpty() = this == null || textLength == 0 || elementType == TokenType.WHITE_SPACE

fun ASTNode.isArrayDelimiter(): Boolean = elementType in ARRAY_DELIMITERS
