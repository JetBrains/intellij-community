// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PySuspiciousBooleanConditionInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass() = PySuspiciousBooleanConditionInspection::class.java

  fun `test coroutine in if condition`() = doTestByText("""
    async def f() -> bool:
        return True


    async def main():
        if <warning descr="Coroutine not awaited in boolean context">f()</warning>:
            print("hi")
    """.trimIndent())

  fun `test coroutine in while condition`() = doTestByText("""
    async def check() -> bool:
        return True


    async def main():
        while <warning descr="Coroutine not awaited in boolean context">check()</warning>:
            print("looping")
    """.trimIndent())

  fun `test coroutine in conditional expression`() = doTestByText("""
    async def condition() -> bool:
        return True


    async def main():
        result = "yes" if <warning descr="Coroutine not awaited in boolean context">condition()</warning> else "no"
        print(result)
    """.trimIndent())

  fun `test coroutine in elif condition`() = doTestByText("""
    async def check1() -> bool:
        return True


    async def check2() -> bool:
        return False


    async def main():
        if <warning descr="Coroutine not awaited in boolean context">check1()</warning>:
            print("first")
        elif <warning descr="Coroutine not awaited in boolean context">check2()</warning>:
            print("second")
        else:
            print("neither")
    """.trimIndent())

  fun `test coroutine variable in if condition`() = doTestByText("""
    async def f() -> bool:
        return True


    async def main():
        coro = f()
        if <warning descr="Coroutine not awaited in boolean context">coro</warning>:
            print("hi")
    """.trimIndent())

  fun `test coroutine in assert statement`() = doTestByText("""
    async def check() -> bool:
        return True


    async def main():
        assert <warning descr="Coroutine not awaited in boolean context">check()</warning>
    """.trimIndent())

  fun `test coroutine with 'not' operator`() = doTestByText("""
    async def f() -> bool:
        return True


    async def main():
        assert not <warning descr="Coroutine not awaited in boolean context">f()</warning>
    """.trimIndent())

  fun `test coroutine variable with 'and' condition`() = doTestByText("""
    async def f() -> bool:
        return True


    async def main():
        coro = f()
        assert bool() and <warning descr="Coroutine not awaited in boolean context">coro</warning>
    """.trimIndent())

  fun `test coroutine with 'or' condition`() = doTestByText("""
    async def f() -> bool:
        return True


    async def main():
        assert (
            <warning descr="Coroutine not awaited in boolean context">f()</warning>
            or <warning descr="Coroutine not awaited in boolean context">f()</warning> 
            or <warning descr="Coroutine not awaited in boolean context">f()</warning> 
        )
    """.trimIndent())

  fun `test coroutine with bare expression`() = doTestByText("""
    async def f() -> bool:
      return True


    async def main():
        a = (
            <warning descr="Coroutine not awaited in boolean context">f()</warning> 
            or <warning descr="Coroutine not awaited in boolean context">f()</warning>
        )
        a = not <warning descr="Coroutine not awaited in boolean context">f()</warning>
  """.trimIndent())

  fun `test awaited coroutine no warning`() = doTestByText("""
    async def f() -> bool:
        return True


    async def main():
        if await f():
            print("hi")
    """.trimIndent())

  fun `test non coroutine no warning`() = doTestByText("""
    def f() -> bool:
        return True


    def main():
        if f():
            print("hi")
    """.trimIndent())

  fun `test shape type no warning`() = doTestByText("""
    def f(val):
      val[0]
      assert val
    """)
}
