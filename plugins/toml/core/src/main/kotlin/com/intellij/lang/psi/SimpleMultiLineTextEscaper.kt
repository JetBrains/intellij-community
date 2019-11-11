/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package com.intellij.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/** Same as [com.intellij.psi.LiteralTextEscaper.createSimple], but multi line */
class SimpleMultiLineTextEscaper<T: PsiLanguageInjectionHost>(host: T) : LiteralTextEscaper<T>(host) {
    override fun decode(rangeInsideHost: TextRange, outChars: java.lang.StringBuilder): Boolean {
        outChars.append(rangeInsideHost.substring(myHost.text))
        return true
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        return rangeInsideHost.startOffset + offsetInDecoded
    }

    override fun isOneLine(): Boolean = false
}
