/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.junit.Assert

abstract class LiteralOffsetsTestBase(
    private val type: IElementType,
    private val text: String,
    private val constructor: (ASTNode) -> LiteralOffsets
) {

    protected fun doTest() {
        val offsets = constructor(
            LeafPsiElement(
                type,
                text.replace("|", "")
            )
        )
        val expected = makeOffsets(text)
        Assert.assertEquals(expected, offsets)
    }

    private fun makeOffsets(text: String): LiteralOffsets {
        val parts = text.split('|')
        assert(parts.size == 5)
        val prefixEnd = parts[0].length
        val openDelimEnd = prefixEnd + parts[1].length
        val valueEnd = openDelimEnd + parts[2].length
        val closeDelimEnd = valueEnd + parts[3].length
        val suffixEnd = closeDelimEnd + parts[4].length
        return LiteralOffsets.fromEndOffsets(
            prefixEnd,
            openDelimEnd,
            valueEnd,
            closeDelimEnd,
            suffixEnd
        )
    }
}
