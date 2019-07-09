// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.mlcompletion.PyCompletionFeatures
import com.jetbrains.python.codeInsight.mlcompletion.PyMlCompletionHelpers
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel

class Py3LocationFeaturesTest: PyTestCase() {
  override fun getTestDataPath(): String = super.getTestDataPath() + "/codeInsight/mlcompletion"

  override fun setUp() {
    super.setUp()
    setLanguageLevel(LanguageLevel.PYTHON35)
  }

  fun testIsInCondition1() = doTestLocationBinaryFeature(PyCompletionFeatures::isInCondition, true)
  fun testIsInCondition2() = doTestLocationBinaryFeature(PyCompletionFeatures::isInCondition, true)
  fun testIsInCondition3() = doTestLocationBinaryFeature(PyCompletionFeatures::isInCondition, false)
  fun testIsInCondition4() = doTestLocationBinaryFeature(PyCompletionFeatures::isInCondition, false)
  fun testIsInCondition5() = doTestLocationBinaryFeature(PyCompletionFeatures::isInCondition, false)
  fun testIsInCondition6() = doTestLocationBinaryFeature(PyCompletionFeatures::isInCondition, true)

  fun testIsInFor1() = doTestLocationBinaryFeature(PyCompletionFeatures::isInForStatement, true)
  fun testIsInFor2() = doTestLocationBinaryFeature(PyCompletionFeatures::isInForStatement, true)
  fun testIsInFor3() = doTestLocationBinaryFeature(PyCompletionFeatures::isInForStatement, true)
  fun testIsInFor4() = doTestLocationBinaryFeature(PyCompletionFeatures::isInForStatement, true)
  fun testIsInFor5() = doTestLocationBinaryFeature(PyCompletionFeatures::isInForStatement, false)

  fun testIsAfterIfWithoutElse1() = doTestLocationBinaryFeature(PyCompletionFeatures::isAfterIfStatementWithoutElseBranch, true)
  fun testIsAfterIfWithoutElse2() = doTestLocationBinaryFeature(PyCompletionFeatures::isAfterIfStatementWithoutElseBranch, false)
  fun testIsAfterIfWithoutElse3() = doTestLocationBinaryFeature(PyCompletionFeatures::isAfterIfStatementWithoutElseBranch, false)
  fun testIsAfterIfWithoutElse4() = doTestLocationBinaryFeature(PyCompletionFeatures::isAfterIfStatementWithoutElseBranch, true)
  fun testIsAfterIfWithoutElse5() = doTestLocationBinaryFeature(PyCompletionFeatures::isAfterIfStatementWithoutElseBranch, false)

  fun testIsDirectlyInArgumentsContext1() = doTestLocationBinaryFeature(PyCompletionFeatures::isDirectlyInArgumentsContext, true)
  fun testIsDirectlyInArgumentsContext2() = doTestLocationBinaryFeature(PyCompletionFeatures::isDirectlyInArgumentsContext, true)
  fun testIsDirectlyInArgumentsContext3() = doTestLocationBinaryFeature(PyCompletionFeatures::isDirectlyInArgumentsContext, false)
  fun testIsDirectlyInArgumentsContext4() = doTestLocationBinaryFeature(PyCompletionFeatures::isDirectlyInArgumentsContext, true)

  fun testPrevNeighbourKeywords1() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevNeighboursKeywordIds, arrayListOf("in"))
  fun testPrevNeighbourKeywords2() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevNeighboursKeywordIds, arrayListOf("in", "not"))

  fun testSameLineKeywords1() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameLine, arrayListOf("in", "if"))
  fun testSameLineKeywords2() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameLine, arrayListOf("in", "if"))

  fun testSameColumnKeywords1() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameColumn, arrayListOf("elif", "if"))
  fun testSameColumnKeywords2() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameColumn, arrayListOf("if"))
  fun testSameColumnKeywords3() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameColumn, arrayListOf("def", "def"))
  fun testSameColumnKeywords4() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameColumn, arrayListOf("def", "def"))
  fun testSameColumnKeywords5() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameColumn, arrayListOf("if", "for"))
  fun testSameColumnKeywords6() = doTestPrevKeywordsFeature(PyCompletionFeatures::getPrevKeywordsIdsInTheSameColumn, arrayListOf("if"))

  private fun doTestPrevKeywordsFeature(f: (PsiElement, Int) -> ArrayList<Int>, expectedPrevKws: ArrayList<String>) {
    val locationPsi = invokeCompletionAndGetLocationPsi()
    val actualPrevKwsIds = f(locationPsi, 2)
    checkPrevKeywordsEquals(expectedPrevKws, actualPrevKwsIds)
  }

  private fun checkPrevKeywordsEquals(expectedPrevKws: ArrayList<String>, actualKeywordsIds: ArrayList<Int>) {
    assertEquals(expectedPrevKws.size, actualKeywordsIds.size)
    actualKeywordsIds.forEachIndexed { index, id ->
      assertEquals(id, PyMlCompletionHelpers.getKeywordId(expectedPrevKws[index]))
    }
  }

  private fun doTestLocationBinaryFeature(f: (PsiElement) -> Boolean, expectedResult: Boolean) {
    val locationPsi = invokeCompletionAndGetLocationPsi()
    assertEquals(expectedResult, f(locationPsi))
  }

  private fun invokeCompletionAndGetLocationPsi(): PsiElement {
    val lookup = invokeCompletionAndGetLookup()
    return lookup.psiElement!!
  }

  private fun invokeCompletionAndGetLookup(): LookupImpl {
    myFixture.configureByFile(getTestName(true) + ".py")
    myFixture.completeBasic()
    return myFixture.lookup as LookupImpl
  }
}