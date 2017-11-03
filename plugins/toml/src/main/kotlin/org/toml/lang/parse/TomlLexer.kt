/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.parse

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class TomlLexer : FlexAdapter(_TomlLexer(null as Reader?))
