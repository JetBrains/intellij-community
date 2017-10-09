/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory

class TomlHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {

    override fun createHighlighter(): TomlHighlighter {
        return TomlHighlighter()
    }

}

