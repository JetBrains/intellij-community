/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

class TomlKeySegmentManipulator : AbstractElementManipulator<TomlKeySegment>() {
    override fun handleContentChange(element: TomlKeySegment, range: TextRange, newContent: String?): TomlKeySegment {
        throw NotImplementedError("This operation is not yet supported")
    }

    override fun getRangeInElement(element: TomlKeySegment): TextRange {
        return TextRange.from(0, element.textLength)
    }
}
