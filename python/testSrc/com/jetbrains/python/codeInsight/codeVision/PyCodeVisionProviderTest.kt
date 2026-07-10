// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.codeVision

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.intellij.idea.TestFor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import kotlin.time.Duration.Companion.milliseconds

@Subsystems.CodeInsight
@Layers.Functional
class PyCodeVisionProviderTest : CodeVisionTestCase() {
  fun testDynamicUsages() = doTest("""
    /*<# block [1 usage] #>*/
    class MyClass:
    /*<# block [2 usages (1 dynamic)] #>*/
        def method(self):
            ...


    class UnrelatedClass:
    /*<# block [1 usage (1 dynamic)] #>*/
        def method(self):
            ...


    def usage(p):
        p.method()


    cc = MyClass()
    cc.method()
  """.trimIndent(), PyReferencesCodeVisionProvider().groupId)

  fun testTwoDynamicUsages() = doTest("""
    /*<# block [1 usage] #>*/
    class MyClass:
    /*<# block [3 usages (2 dynamic)] #>*/
        def method(self):
            ...


    def usage(p):
        p.method()
        p.method()


    cc = MyClass()
    cc.method()
  """.trimIndent(), PyReferencesCodeVisionProvider().groupId)

  fun testLocalDefinitionsNotAnalysed() = doTest("""
    /*<# block [1 usage] #>*/
    def foo():
        class MyClass:
            ...
        
        def bar():
            ...
        
        mc = MyClass()
        bar()
        
        
    foo()
  """.trimIndent(), PyReferencesCodeVisionProvider().groupId)

  fun testMagicMethodUsagesNotAnalysed() = doTest("""
    /*<# block [2 usages] #>*/
    class MyClass:
        def __add__(self, other):
            return other


    a = MyClass()
    b = MyClass()
    c = a + b
    d = a.__add__(b)
  """.trimIndent(), PyReferencesCodeVisionProvider().groupId)

  fun testInnerClassNotAnalysed() = doTest("""
    /*<# block [2 usages] #>*/
    class MyClass:
        class MyInnerClass:
            def inner_foo(self):
                ...

    /*<# block [1 usage] #>*/
        def foo():
            ...


    mc = MyClass()
    mc.foo()
    mic = MyClass.MyInnerClass()
    mic.inner_foo()
  """.trimIndent(), PyReferencesCodeVisionProvider().groupId)

  @TestFor(issues=["PY-82336"])
  fun testUsagesInManyFiles() {
    // The symbol is "widespread", so the search is time-bounded; pin a generous budget so a cold/slow
    // run still computes the exact count instead of flakily reporting a truncated "N+".
    pyUsagesSearchBudgetTestOverride = 60_000.milliseconds
    try {
      addTargetUsageFiles()
      // 11 call sites; the 11 `from defs import target` statements are imports, not usages, so are not counted.
      doTest("""
        /*<# block [11 usages] #>*/
        def target():
            ...
      """.trimIndent(), PyReferencesCodeVisionProvider().groupId, fileName = "defs.py")
    }
    finally {
      pyUsagesSearchBudgetTestOverride = null
    }
  }

  // When the number of usages exceeds the `python.code.vision.usages.limit` cap, the count is
  // truncated and rendered with a trailing "+".
  fun testUsagesCountIsCappedWithPlus() {
    val limitId = "python.code.vision.usages.limit"
    val previousLimit = AdvancedSettings.getInt(limitId)
    AdvancedSettings.setInt(limitId, 2)
    // The symbol is "widespread", so the wall-clock budget also applies; pin a generous budget so the
    // truncation is driven solely by the limit cap and a cold/slow run can't flakily report a smaller count.
    pyUsagesSearchBudgetTestOverride = 60_000.milliseconds
    try {
      addTargetUsageFiles()
      doTest("""
        /*<# block [2+ usages] #>*/
        def target():
            ...
      """.trimIndent(), PyReferencesCodeVisionProvider().groupId, fileName = "defs.py")
    }
    finally {
      pyUsagesSearchBudgetTestOverride = null
      AdvancedSettings.setInt(limitId, previousLimit)
    }
  }

  // Creates more files referencing `target` than `ide.unused.symbol.calculation.maxFilesToSearchUsagesIn`
  // (default 10), so the symbol counts as "too widespread for a cheap search". Each file has one import and
  // one call; imports are not counted as usages, leaving 11 usages.
  private fun addTargetUsageFiles() {
    repeat(11) { i ->
      myFixture.addFileToProject("usage_$i.py", """
        from defs import target
        target()
      """.trimIndent())
    }
  }

  private fun doTest(text: String, vararg enabledProviderGroupIds: String, fileName: String = "test.py") {
    testProviders(text, fileName, *enabledProviderGroupIds)
  }
}