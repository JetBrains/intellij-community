// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiElement
import com.jetbrains.python.fixtures.PyResolveTestCase
import com.jetbrains.python.psi.*


class PyTypeShedConditionCheckerTest: PyResolveTestCase() {

  fun testStubConditionalImport_matchingIf() {
    myFixture.configureByText("test.py", """
        import foo
        
        foo.bar<ref>()
      """.trimIndent())
    doConditionalImportTest("a", LanguageLevel.PYTHON312)
  }

  fun testStubConditionalImport_matchingElse() {
    myFixture.configureByText("test.py", """
        from foo import bar
        
        bar<ref>()
      """.trimIndent())
    doConditionalImportTest("b", LanguageLevel.PYTHON38)
  }

  fun testStubConditionalClassMethod_matchingIf() {
    myFixture.configureByText("test.py", """
        import foo
        
        foo.Foo().bar<ref>()
      """.trimIndent())
    doConditionalClassMethodTest("if", LanguageLevel.PYTHON312)
  }

  fun testStubConditionalClassMethod_matchingElIf() {
    myFixture.configureByText("test.py", """
        from foo import Foo
        
        Foo().bar<ref>()
      """.trimIndent())
    doConditionalClassMethodTest("elif", LanguageLevel.PYTHON36)
  }

  fun testStubConditionalClassMethod_matchingElse() {
    myFixture.configureByText("test.py", """
        import foo
        
        f = foo.Foo()
        
        f.bar<ref>()
      """.trimIndent())
    doConditionalClassMethodTest("else", LanguageLevel.PYTHON38)
  }

  override fun doResolve(): PsiElement? {
    return findReferenceByMarker(myFixture.file).resolve()
  }

  private fun doConditionalImportTest(expectedPackageImport: String, languageLevel: LanguageLevel) {
    myFixture.copyDirectoryToProject("resolve/StubConditionalImport", "")
    return doConditionalTest(expectedPackageImport, languageLevel) { element ->
      val target = assertResolveResult(element, PyTargetExpression::class.java, "bar")
      val func = assertResolveResult(target.findAssignedValue()?.reference?.resolve(), PyFunction::class.java, "bar")
      val a = func.parameterList.findParameterByName("a")
      PyStringLiteralCoreUtil.stripQuotesAroundValue(a!!.defaultValueText)
    }
  }

  private fun doConditionalClassMethodTest(expectedIfPart: String, languageLevel: LanguageLevel) {
    myFixture.copyDirectoryToProject("resolve/StubConditionalClassMethod", "")

    val platform = if (SystemInfo.isWindows) "win32_"
    else if (SystemInfo.isMac) "darwin_"
    else if (SystemInfo.isLinux) "linux_"
    else ""

    return doConditionalTest("$platform$expectedIfPart", languageLevel) { element ->
      val func = assertResolveResult(element, PyFunction::class.java, "bar")
      val a = func.parameterList.findParameterByName("a")
      PyStringLiteralCoreUtil.stripQuotesAroundValue(a!!.defaultValueText)
    }
  }

  private fun doConditionalTest(expectedValue: String, languageLevel: LanguageLevel, valueExtractor: (el:PsiElement?) -> String) {
    runWithLanguageLevel(languageLevel) {
      assertEquals(expectedValue, valueExtractor(doResolve()))
    }
  }
}