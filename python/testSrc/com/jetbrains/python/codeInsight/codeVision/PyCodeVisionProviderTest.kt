// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.codeVision

import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase

class PyCodeVisionProviderTest : CodeVisionTestCase() {
  fun testDynamicUsages() = doTest("""
    <# block [1 usage] #>
    class MyClass:
    <# block [2 usages (1 dynamic)] #>
        def method(self):
            ...


    class UnrelatedClass:
    <# block [1 usage (1 dynamic)] #>
        def method(self):
            ...


    def usage(p):
        p.method()


    cc = MyClass()
    cc.method()
  """.trimIndent())

  fun testTwoDynamicUsages() = doTest("""
    <# block [1 usage] #>
    class MyClass:
    <# block [3 usages (2 dynamic)] #>
        def method(self):
            ...


    def usage(p):
        p.method()
        p.method()


    cc = MyClass()
    cc.method()
  """.trimIndent())

  fun testLocalDefinitionsNotAnalysed() = doTest("""
    <# block [1 usage] #>
    def foo():
        class MyClass:
            ...
        
        def bar():
            ...
        
        mc = MyClass()
        bar()
        
        
    foo()
  """.trimIndent())

  fun testMagicMethodUsagesNotAnalysed() = doTest("""
    <# block [2 usages] #>
    class MyClass:
        def __add__(self, other):
            return other


    a = MyClass()
    b = MyClass()
    c = a + b
    d = a.__add__(b)
  """.trimIndent())

  fun testInnerClassNotAnalysed() = doTest("""
    <# block [2 usages] #>
    class MyClass:
        class MyInnerClass:
            def inner_foo(self):
                ...

    <# block [1 usage] #>
        def foo():
            ...


    mc = MyClass()
    mc.foo()
    mic = MyClass.MyInnerClass()
    mic.inner_foo()
  """.trimIndent())

  private fun doTest(text: String) {
    testProviders(text, "test.py")
  }
}