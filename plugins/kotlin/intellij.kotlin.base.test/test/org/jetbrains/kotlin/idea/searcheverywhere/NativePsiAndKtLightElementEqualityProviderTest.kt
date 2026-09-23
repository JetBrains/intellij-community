// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiClass
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiMethods
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * @see KtSearchEverywhereEqualityProvider
 */
open class NativePsiAndKtLightElementEqualityProviderTest : KotlinSearchEverywhereTestCase() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test only class presented`() {
        val file = myFixture.configureByText("MyKotlinClassWithStrangeName.kt", "class MyKotlinClassWithStrangeName")
        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        // FIXME: KTIJ-40145
        val ulc = allowAnalysisOnEdt {
            analyze(klass) {
                klass.classSymbol?.asPsiClass()!!
            }
        }
        findPsiByPattern("MyKotlinClassWithStrangeName") { results ->
            assertTrue(klass in results)
            assertFalse(file in results)
            assertFalse(ulc in results)
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test class conflict`() {
        val file = myFixture.configureByText(
            "MyKotlinClassWithStrangeName.kt",
            "package one.two\nclass MyKotlinClassWithStrangeName\nclass MyKotlinClassWithStrangeName<T>",
        ) as KtFile

        val klass = file.declarations.first() as KtClass
        val klass2 = file.declarations.last() as KtClass
        // FIXME: KTIJ-40145
        allowAnalysisOnEdt {
            analyze(file) {
                val ulc = klass.classSymbol?.asPsiClass()!!
                val ulc2 = klass2.classSymbol?.asPsiClass()!!
                findPsiByPattern("MyKotlinClassWithStrangeName") { results ->
                    assertTrue(klass in results)
                    assertTrue(klass2 in results)
                    assertFalse(file in results)
                    assertFalse(ulc in results)
                    assertFalse(ulc2 in results)
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test class and file presented`() {
        val file = myFixture.configureByText(
            "MyKotlinClassWithStrangeName.kt",
            "class MyKotlinClassWithStrangeName\nfun t(){}",
        ) as KtFile

        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        // FIXME: KTIJ-40145
        allowAnalysisOnEdt {
            analyze(file) {
                val ulc = klass.classSymbol?.asPsiClass()!!
                val syntheticClass = (file.declarations.last() as KtNamedFunction).symbol.asPsiMethods().single().parent
                findPsiByPattern("MyKotlinClassWithStrangeName") { results ->
                    assertTrue(results.toString(), results.size == 1)
                    assertTrue(klass in results)
                    assertFalse(file in results)
                    assertFalse(syntheticClass in results)
                    assertFalse(ulc in results)
                }
            }
        }
    }
}
