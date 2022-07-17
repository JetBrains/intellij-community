/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.lang.Language

object TomlLanguage : Language("TOML", "text/toml") {
    override fun isCaseSensitive() = true
}
