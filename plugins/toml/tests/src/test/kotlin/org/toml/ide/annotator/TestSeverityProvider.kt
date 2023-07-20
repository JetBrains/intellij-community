/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

class TestSeverityProvider(private val severities: List<HighlightSeverity>) : SeveritiesProvider() {
    override fun getSeveritiesHighlightInfoTypes(): List<HighlightInfoType> = severities.map(::TestHighlightingInfoType)
}

private class TestHighlightingInfoType(private val severity: HighlightSeverity) : HighlightInfoType {
    override fun getAttributesKey(): TextAttributesKey = DEFAULT_TEXT_ATTRIBUTES
    override fun getSeverity(psiElement: PsiElement?): HighlightSeverity = severity

    companion object {
        private val DEFAULT_TEXT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("DEFAULT_TEXT_ATTRIBUTES")
    }
}
