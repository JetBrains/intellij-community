// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.jetbrains.env.PyAbstractTestProcessRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class PyTestMagnitudeTest {

  private lateinit var root: SMTestProxy.SMRootTestProxy
  private val tree: String get() = PyAbstractTestProcessRunner.getFormattedTestTree(root)

  private val test11 = "test11"


  @Before
  fun setUp() {
    root = SMTestProxy.SMRootTestProxy()

    val file1 = SMTestProxy("file1", false, null)
    val test11 = SMTestProxy(test11, false, null)
    val test12 = SMTestProxy("test12", false, null)

    val file2 = SMTestProxy("file2", false, null)
    val test21 = SMTestProxy("test21", false, null)
    val test22 = SMTestProxy("test22", false, null)

    file1.addChild(test11)
    file1.addChild(test12)

    file2.addChild(test21)
    file2.addChild(test22)

    root.addChild(file1)
    root.addChild(file2)
  }

  @Test
  fun testSuccessPass() {
    root.children.forEach { file ->
      file.children.forEach { test ->
        test.setStarted()
        test.setFinished()
      }
    }
    Assert.assertEquals(TestStateInfo.Magnitude.PASSED_INDEX, root.calculateAndReturnMagnitude())
    Assert.assertEquals("Test tree:\n" +
                        "[root](+)\n" +
                        ".file1(+)\n" +
                        "..test11(+)\n" +
                        "..test12(+)\n" +
                        ".file2(+)\n" +
                        "..test21(+)\n" +
                        "..test22(+)\n", tree)
  }


  @Test
  fun testFailedOne() {
    root.children.forEach { file ->
      file.children.forEach { test ->
        test.setStarted()
        if (test.name == test11) test.setTestFailed("fail", null, false) else test.setFinished()
      }
    }
    Assert.assertEquals(TestStateInfo.Magnitude.FAILED_INDEX, root.calculateAndReturnMagnitude())
    Assert.assertEquals("Test tree:\n" +
                        "[root](-)\n" +
                        ".file1(-)\n" +
                        "..test11(-)\n" +
                        "..test12(+)\n" +
                        ".file2(+)\n" +
                        "..test21(+)\n" +
                        "..test22(+)\n", tree)
  }

  @Test
  fun testTerminatedOne() {
    root.children.forEach { file ->
      file.children.forEach { test ->
        test.setStarted()
        if (test.name != test11) {
          test.setFinished()
        }
      }
    }
    Assert.assertEquals(TestStateInfo.Magnitude.TERMINATED_INDEX, root.calculateAndReturnMagnitude())
    Assert.assertEquals("Test tree:\n" +
                        "[root][T]\n" +
                        ".file1[T]\n" +
                        "..test11[T]\n" +
                        "..test12(+)\n" +
                        ".file2(+)\n" +
                        "..test21(+)\n" +
                        "..test22(+)\n", tree)
  }

  @Test
  fun testIgnoredAll() {
    root.children.forEach { file ->
      file.children.forEach { test ->
        test.setStarted()
        test.setTestIgnored("for a good reason", null)

      }
    }
    Assert.assertEquals(TestStateInfo.Magnitude.IGNORED_INDEX, root.calculateAndReturnMagnitude())
    Assert.assertEquals("Test tree:\n" +
                        "[root](~)\n" +
                        ".file1(~)\n" +
                        "..test11(~)\n" +
                        "..test12(~)\n" +
                        ".file2(~)\n" +
                        "..test21(~)\n" +
                        "..test22(~)\n", tree)
  }
}
