/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import org.toml.lang.core.psi.TomlTypes.STRING

class TomlQuoteHandler : SimpleTokenSetQuoteHandler(STRING)
