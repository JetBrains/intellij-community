// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.Location
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.run.AbstractPythonRunConfiguration

/**
 * Parent of all test configurations
 *
 * @author Ilya.Kazakevich
 */
abstract class AbstractPythonTestRunConfiguration<T : AbstractPythonTestRunConfiguration<T>>
protected constructor(project: Project, factory: ConfigurationFactory) : AbstractPythonRunConfiguration<T>(project, factory) {
  /**
   * Create test spec (string to be passed to runner, probably glued with [TEST_NAME_PARTS_SPLITTER])
   *
   * @param location   test location as reported by runner
   * @param failedTest failed test
   * @return string spec or null if spec calculation is impossible
   */
  open fun getTestSpec(location: Location<*>, failedTest: AbstractTestProxy): String? {
    val element = location.psiElement
    var pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
    if (location is PyPsiLocationWithFixedClass) {
      pyClass = location.fixedClass
    }
    val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
    val virtualFile = location.virtualFile
    if (virtualFile != null) {
      var path = virtualFile.canonicalPath
      if (pyClass != null) {
        path += TEST_NAME_PARTS_SPLITTER + pyClass.name
      }
      if (pyFunction != null) {
        path += TEST_NAME_PARTS_SPLITTER + pyFunction.name
      }
      return path
    }
    return null
  }

  companion object {
    /**
     * When passing path to test to runners, you should join parts with this char.
     * I.e.: file.py::PyClassTest::test_method
     */
    protected const val TEST_NAME_PARTS_SPLITTER = "::"
  }
}