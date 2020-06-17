/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.toml.ide.colors.TomlColor
import org.toml.lang.psi.TomlElementTypes

class TomlHighlightingAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        // Although we use only elementType info, it's not possible to do it by lexer
        // because numbers and dates can be used as keys too (for example, `123 = 123` is correct toml)
        // and proper element types are set by parser.
        // see `org.toml.lang.parse.TomlParserUtil.remap`
        val color = when (element.node.elementType) {
            TomlElementTypes.NUMBER -> TomlColor.NUMBER
            TomlElementTypes.DATE_TIME -> TomlColor.DATE
            TomlElementTypes.BARE_KEY -> TomlColor.KEY
            else -> return
        }
        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

        holder.newSilentAnnotation(severity).textAttributes(color.textAttributesKey).create()
    }
}
