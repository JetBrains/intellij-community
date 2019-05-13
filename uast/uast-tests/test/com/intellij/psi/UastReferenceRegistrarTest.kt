// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi

import com.intellij.patterns.ElementPattern
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ProcessingContext
import junit.framework.TestCase
import org.junit.Test

class UastReferenceRegistrarTest : LightCodeInsightFixtureTestCase() {

  @Test
  fun testUastReferenceContributorCalledOnlyOnLiterals() {

    /**
     * This implementation is used instead of [ReferenceProvidersRegistry] to emulate behaviour that [PsiReferenceProvider] will be called on
     * each [PsiElement] not only [ContributedReferenceHost]-s as it is usually done for Java
     */
    val psiReferenceRegistrar = object : PsiReferenceRegistrar() {

      private val providers = mutableListOf<Pair<ElementPattern<*>, PsiReferenceProvider>>()

      fun getReferencesFor(psiElement: PsiElement): List<PsiReference> {
        val context = ProcessingContext()
        return providers.flatMap { (pattern, provider) ->
          if (pattern.accepts(psiElement, context) && provider.acceptsTarget(psiElement))
            provider.getReferencesByElement(psiElement, context).toList()
          else emptyList()
        }
      }

      override fun <T : PsiElement?> registerReferenceProvider(pattern: ElementPattern<T>,
                                                               provider: PsiReferenceProvider,
                                                               priority: Double) {
        providers += pattern to provider
      }

    }

    val expectedInvocationCount = 10
    var invocationCount = 0

    psiReferenceRegistrar.registerUastReferenceProvider(
      { _, _ -> invocationCount++; true },
      uastLiteralReferenceProvider { _, _ -> PsiReference.EMPTY_ARRAY }
    )

    myFixture.addClass("""
      class MyClass {
          String foo(){
             return new java.lang.StringBuilder()
             ${(1..10).joinToString("\n") { ".append(\"$it\")" }}
             .toString();
          }
      }
    """.trimIndent())


    myFixture.configureByFile("MyClass.java")
    myFixture.file.accept(object : JavaRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        psiReferenceRegistrar.getReferencesFor(element)
        super.visitElement(element)
      }
    })
    TestCase.assertEquals(expectedInvocationCount, invocationCount)

  }

}