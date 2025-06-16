// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyInconsistentReturnsInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection> = PyInconsistentReturnsInspection::class.java
  override fun doTestByText(text: String) = super.doTestByText(text.trimIndent())

  // --------------------------
  // Basic Return Tests
  // --------------------------

  fun testNoReturns() = doTestByText("""
        def f():
            x = 1
    """)

  fun testOnlyExplicitReturn() = doTestByText("""
        def f():
            return 42
    """)

  fun testOnlyImplicitNoneNoExplicit() = doTestByText("""
        def f():
            return
    """)

  fun testExplicitReturnAndImplicitNone() = doTestByText("""
        def f(cond):
            if cond:
                return 10
            <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
    """)

  fun testExplicitReturnAndImplicitStmt() = doTestByText("""
        def f(cond):
            <weak_warning descr="Missing return statement on some paths">if cond:</weak_warning>
                return 1
    """)

  // --------------------------
  // If Tests
  // --------------------------

  fun testIfElse() = doTestByText("""
        def f(cond, cond2, cond3):
            if cond:
                return 'yes'
            elif cond2:
                <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
            elif cond3:
                <weak_warning descr="Missing return statement on some paths">print()</weak_warning>
            else:
                return 'no'
    """)

  fun testIfNoElse() = doTestByText("""
        def f(cond, cond2, cond3):
            <weak_warning descr="Missing return statement on some paths">if cond:</weak_warning>
                return 'yes'
            elif cond2:
                <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
            elif cond3:
                print()
  """)

  // --------------------------
  // While Tests
  // --------------------------

  fun testWhileTrueNoWarning() = doTestByText("""
        def f():
            while True:
                do_something()
    """)

  fun testWhile() = doTestByText("""
        def f():
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">while not x:</weak_warning>
                break
    """)

  fun testWhileImplicitNone() = doTestByText("""
        def f():
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">while not x:</weak_warning>
                <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
    """)

  fun testWhileWithElse() = doTestByText("""
        def f():
            if x:
                return 1
            while not x:
                <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
            else:
                <weak_warning descr="Missing return statement on some paths">x = 5</weak_warning>
    """)

  // --------------------------
  // For Tests
  // --------------------------

  fun testForNoElse() = doTestByText("""
        def f(x):
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">for i in range(10):</weak_warning>
                if i > 10:
                    <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
    """)

  fun testForLoopIncomplete() = doTestByText("""
        def x(y):
            for i in range(10):
                if i > 10:
                    return i
            <weak_warning descr="Missing return statement on some paths">pass</weak_warning>
  """)

  fun testForLoopWithElsePrint() = doTestByText("""
        def x(y):
            if y:
                return 1
            for i in range(10):
                if i > 10:
                    break
            else:
                <weak_warning descr="Missing return statement on some paths">print()</weak_warning>
  """)

  // --------------------------
  // Assert Tests
  // --------------------------

  fun testAssertTrue() = doTestByText("""
        def f(x):
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">assert True</weak_warning>
    """)

  fun testAssertUndecidable() = doTestByText("""
        def f(x):
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">assert x</weak_warning>
    """)

  fun testAssertFalseNoWarning() = doTestByText("""
        def f(x):
            if x:
                return 1
            assert False
    """)

  // --------------------------
  // Function Call Tests
  // --------------------------

  fun testNormalCallExpression() = doTestByText("""
        def f(x):
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">foo()</weak_warning>
    """)

  fun testNoReturnCall() = doTestByText("""
        import sys
        def f(x):
            if x:
                return 1
            sys.exit()
    """)


  // --------------------------
  // Other Statement Tests
  // --------------------------

  fun testTryExceptNoWarning() = doTestByText("""
        def f(x):
            if x:
                return 1
            try:
                risky()
            except Exception:
                handle()
    """)

  fun testMatchStatement() = doTestByText("""
        def f(x):
            if x:
                return 1
            match x:
                case 1:
                    <weak_warning descr="Missing return statement on some paths">foo()</weak_warning>
                case _:
                    <weak_warning descr="Missing return statement on some paths">bar()</weak_warning>
    """)

  fun testWithStatement() = doTestByText("""
        def f():
            if x:
                return 1
            with ctx():
                <weak_warning descr="Missing return statement on some paths">body()</weak_warning>
    """)

  fun testWithStatementSuppressingExceptions() = doTestByText("""
        class ctx:
            def __enter__(self): ...
            def __exit__(self, exc_type, exc_val, exc_tb) -> bool: ...
    
        def f():
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">with ctx():</weak_warning>
                body()
    """)

  fun testNestedFunctions() = doTestByText("""
        def f(x):
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">def g():</weak_warning>
                return 2
    """)

  fun testNestedClass() = doTestByText("""
        def f(x):
            if x:
                return 1
            <weak_warning descr="Missing return statement on some paths">class C:</weak_warning>
                x = 5
    """)


  fun testNonexistentFunctionCall() = doTestByText("""
    def func_unknown(x):
        if x > 0:
            return False
        <weak_warning descr="Missing return statement on some paths">no_such_function()</weak_warning>
""")

  fun testNoReturnFunction() = doTestByText("""
    def func_no_noreturn(x):
        if x > 0:
            return False
        <weak_warning descr="Missing return statement on some paths">print("", end="")</weak_warning>
""")

  fun testMatchStatementInconsistentReturns() = doTestByText("""
    def x(y):
        <weak_warning descr="Missing return statement on some paths">match y:</weak_warning>
            case 0:
                return 1
            case 1:
                print()
""")

  // PY-80524
  fun testIfInsideTryExcept() = doTestByText("""
    def fn():
        try:
            if some_condition():
                return True
            # no warning should be highlighted here
            some_non_returning_function()
        except Exception:
            return False
  """)

}