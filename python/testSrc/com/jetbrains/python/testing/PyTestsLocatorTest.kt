// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.impl.PyClassImpl

class PyTestsLocatorTest : PyTestCase() {

  fun testMethodInClass() {
    val testFile = """
      class Test:
          def test_1(self):
              assert False
        """.trimIndent()

    myFixture.configureByText("test_.py", testFile)

    val locations = PyTestsLocator.getLocation("python<temp:///root>", "test_.Test.test_1", myFixture.project, myFixture.module.getModuleWithDependenciesScope())

    assertSize(1, locations)
    val element = locations[0].psiElement
    assertInstanceOf(element, PyFunction::class.java)
    val function = element as PyFunction
    assertEquals("test_1", function.name)

    val innerClass = function.containingClass
    assertNotNull(innerClass)
    assertInstanceOf(innerClass, PyClass::class.java)
    assertEquals("Test", (innerClass as PyClass).name)
  }

  fun testInnerClass() {
    val testFile = """
      class TestOuter:
          class TestInner:
              def test_1(self):
                  assert False
        """.trimIndent()

    myFixture.configureByText("test.py", testFile)

    val locations = PyTestsLocator.getLocation("python<temp:///root>", "test.TestOuter.TestInner", myFixture.project, myFixture.module.getModuleWithDependenciesScope())

    assertSize(1, locations)
    val element = locations[0].psiElement
    assertInstanceOf(element, PyClassImpl::class.java)
    val innerClass = element as PyClassImpl
    assertEquals("TestInner", innerClass.name)
  }

  fun testMethodInInnerClass() {
    val testFile = """
      class TestOuter:
          class TestInner:
              def test_1(self):
                  assert False
        """.trimIndent()

    myFixture.configureByText("test.py", testFile)

    val locations = PyTestsLocator.getLocation("python<temp:///root>", "test.TestOuter.TestInner.test_1", myFixture.project, myFixture.module.getModuleWithDependenciesScope())

    assertSize(1, locations)
    val element = locations[0].psiElement
    assertInstanceOf(element, PyFunction::class.java)
    val function = element as PyFunction
    assertEquals("test_1", function.name)
  }

  fun testMethodInNestedInnerClass() {
    val testFile = """
      class TestOuter1:
          class TestOuter2:
              class TestInner:
                  def test_1(self):
                      assert False
      """.trimIndent()

    myFixture.configureByText("test.py", testFile)

    val locations = PyTestsLocator.getLocation("python<temp:///root>", "test.TestOuter1.TestOuter2.TestInner.test_1", myFixture.project, myFixture.module.getModuleWithDependenciesScope())

    assertSize(1, locations)
    val element = locations[0].psiElement
    assertInstanceOf(element, PyFunction::class.java)
    val function = element as PyFunction
    assertEquals("test_1", function.name)
  }

  fun testNestedInnerClass() {
    val testFile = """
      class TestOuter1:
          class TestOuter2:
              class TestInner:
                  def test_1(self):
                      assert False
      """.trimIndent()

    myFixture.configureByText("test.py", testFile)

    val locations = PyTestsLocator.getLocation("python<temp:///root>", "test.TestOuter1.TestOuter2.TestInner", myFixture.project, myFixture.module.getModuleWithDependenciesScope())

    assertSize(1, locations)
    val element = locations[0].psiElement
    assertInstanceOf(element, PyClassImpl::class.java)
    val innerClass = element as PyClassImpl
    assertEquals("TestInner", innerClass.name)
  }
}
