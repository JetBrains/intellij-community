// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.j2k.copyPaste.ElementAndTextList
import org.jetbrains.kotlin.j2k.copyPaste.convertCodeToKotlin
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Regression test for KTIJ-37629.
 *
 * When the paste target's Kotlin source module can't be resolved, `JavaToKotlinConverter.elementsToKotlin`
 * returns `Result.EMPTY` (no per-element results). The conversion loop must not index past the empty results
 * list; it should fall back to inserting the original Java text unchanged.
 */
class J2KCopyPasteEmptyResultsConversionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testConvertWithUnresolvableTargetModuleDoesNotThrow() {
        val javaFile = myFixture.configureByText("C.java", "public class C {}")
        val javaElement = javaFile.firstChild ?: error("Expected a Java PSI element to convert")

        // A dangling in-memory KtFile has no resolvable source module, so the converter yields Result.EMPTY.
        val target = KtPsiFactory(project).createFile("Target.kt", "")

        val result = ElementAndTextList(listOf<Any>(javaElement)).convertCodeToKotlin(project, target)

        // Before the fix: IndexOutOfBoundsException. After: graceful fallback to the original Java text.
        assertFalse("No conversion expected when the target module is unresolved", result.isTextChanged)
        assertEquals(javaElement.text, result.text)
    }
}
