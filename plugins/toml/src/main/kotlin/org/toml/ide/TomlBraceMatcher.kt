/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.toml.lang.core.psi.TomlTypes

class TomlBraceMatcher() : PairedBraceMatcher {

    override fun getPairs() = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int
        = openingBraceOffset

    companion object {
        private val PAIRS: Array<BracePair> = arrayOf(
            BracePair(TomlTypes.LBRACE, TomlTypes.RBRACE, false),
            BracePair(TomlTypes.LBRACKET, TomlTypes.RBRACKET, false)
        )
    }
}

