// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.lookup.LookupElement
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel

class Py3ElementFeaturesTest: PyTestCase() {
  override fun setUp() {
    super.setUp()
    setLanguageLevel(LanguageLevel.PYTHON35)
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + "/codeInsight/mlcompletion"

  fun testNumberOfOccurrencesFunction() = doTestNumberOfOccurrences("min", PyCompletionMlElementKind.FUNCTION, true, 1)

  fun testNumberOfOccurrencesClass() = doTestNumberOfOccurrences("MyClazz", PyCompletionMlElementKind.TYPE_OR_CLASS, false, 1)

  fun testNumberOfOccurrencesNamedArgs1() = doTestNumberOfOccurrences("end=", PyCompletionMlElementKind.NAMED_ARG, false, 1)

  fun testNumberOfOccurrencesNamedArgs2() = doTestNumberOfOccurrences("file=", PyCompletionMlElementKind.NAMED_ARG, false, 0)

  fun testNumberOfOccurrencesPackagesOrModules() = doTestNumberOfOccurrences("collections", PyCompletionMlElementKind.PACKAGE_OR_MODULE, false, 1)

  fun testKindNamedArg() = doTestElementInfo("sep=", PyCompletionMlElementKind.NAMED_ARG, false)

  fun testClassBuiltins() = doTestElementInfo("Exception", PyCompletionMlElementKind.TYPE_OR_CLASS, true)

  fun testClassNotBuiltins() = doTestElementInfo("MyClazz", PyCompletionMlElementKind.TYPE_OR_CLASS, false)

  fun testFunctionBuiltins() = doTestElementInfo("max", PyCompletionMlElementKind.FUNCTION, true)

  fun testFunctionNotBuiltins() = doTestElementInfo("my_not_builtins_function", PyCompletionMlElementKind.FUNCTION, false)

  fun testKindPackageOrModule() = doTestElementInfo("sys", PyCompletionMlElementKind.PACKAGE_OR_MODULE, false)

  fun testKindFromTarget1() = doTestElementInfo("local_variable", PyCompletionMlElementKind.FROM_TARGET, false)

  fun testKindFromTarget2() = doTestElementInfo("as_target", PyCompletionMlElementKind.FROM_TARGET, false)

  fun testKindKeyword() = doTestElementInfo("if", PyCompletionMlElementKind.KEYWORD, false)

  private fun invokeCompletionAndGetLookupElement(elementName: String): LookupElement? {
    myFixture.configureByFile(getTestName(true) + ".py")
    myFixture.completeBasic()
    val elements = myFixture.lookupElements!!
    return elements.find { it.lookupString == elementName }
  }

  private fun doTestElementInfo(elementName: String, expectedKind: PyCompletionMlElementKind, expectedIsBuiltins: Boolean) {
    val lookupElement = invokeCompletionAndGetLookupElement(elementName)!!
    val info = PyCompletionFeatures.getPyLookupElementInfo(lookupElement)!!
    assertEquals(expectedKind, info.kind)
    assertEquals(expectedIsBuiltins, info.isBuiltins)
  }

  private fun doTestNumberOfOccurrences(elementName: String, expectedKind: PyCompletionMlElementKind, expectedIsBuiltins: Boolean, expectedNumberOfOccurrences: Int) {
    doTestElementInfo(elementName, expectedKind, expectedIsBuiltins)
    val num = PyCompletionFeatures.getNumberOfOccurrencesInScope(expectedKind, myFixture.lookup.psiElement!!, elementName)
    assertEquals(expectedNumberOfOccurrences, num)
  }
}