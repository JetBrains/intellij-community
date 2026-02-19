/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/** See `com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper` */
abstract class LiteralTextEscaperBase<T : PsiLanguageInjectionHost>(host: T) : LiteralTextEscaper<T>(host) {

    private var outSourceOffsets: IntArray? = null

    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        val subText = rangeInsideHost.substring(myHost.text)
        val (offsets, result) = parseStringCharacters(subText, outChars)
        outSourceOffsets = offsets
        return result
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        val outSourceOffsets = outSourceOffsets!!
        val result = if (offsetInDecoded < outSourceOffsets.size) outSourceOffsets[offsetInDecoded] else -1
        return if (result == -1) {
            -1
        } else {
            (if (result <= rangeInsideHost.length) result else rangeInsideHost.length) + rangeInsideHost.startOffset
        }
    }

    protected abstract fun parseStringCharacters(chars: String, outChars: StringBuilder): Pair<IntArray, Boolean>
}
