/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.toml.lang.TomlLanguage


class TomlTokenType(debugName: String) : IElementType(debugName, TomlLanguage)
