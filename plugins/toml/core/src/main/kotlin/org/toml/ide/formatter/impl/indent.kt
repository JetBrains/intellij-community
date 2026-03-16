/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.impl

import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import org.toml.ide.formatter.TomlFmtBlock
import org.toml.lang.psi.TomlElementTypes.ARRAY
import org.toml.lang.psi.TomlElementTypes.INLINE_TABLE

fun TomlFmtBlock.computeIndent(child: ASTNode): Indent? = when (node.elementType) {
    ARRAY -> getBlockNodeIndent(child.isArrayDelimiter())
    INLINE_TABLE -> getBlockNodeIndent(child.isInlineTableDelimiter())
    else -> Indent.getNoneIndent()
}

private fun getBlockNodeIndent(isBlockDelimiter: Boolean): Indent =
    when {
        isBlockDelimiter -> Indent.getNoneIndent()
        else -> Indent.getNormalIndent()
    }
