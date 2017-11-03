/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.parse

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.tree.IElementType


object TomlParserUtil : GeneratedParserUtilBase() {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun remap(b: PsiBuilder, level: Int, from: IElementType, to: IElementType): Boolean {
        if (b.tokenType == from) {
            b.remapCurrentToken(to)
            b.advanceLexer()
            return true
        }
        return false
    }
}
