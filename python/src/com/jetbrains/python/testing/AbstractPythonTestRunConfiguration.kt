// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.Location
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.getTargetEnvironmentValueForLocalPath
import com.intellij.execution.target.value.joinToStringFunction
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.run.AbstractPythonRunConfiguration

/**
 * Parent of all test configurations
 *
 * @author Ilya.Kazakevich
 */
abstract class AbstractPythonTestRunConfiguration<T : AbstractPythonTestRunConfiguration<T>>
@JvmOverloads
protected constructor(project: Project, factory: ConfigurationFactory, val requiredPackage: String? = null) :
  AbstractPythonRunConfiguration<T>(project, factory) {
  /**
   * Create test spec (string to be passed to runner, probably glued with [TEST_NAME_PARTS_SPLITTER])
   *
   * *To be deprecated. The part of the legacy implementation based on [com.intellij.execution.configurations.GeneralCommandLine].*
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

  open fun getTestSpec(request: TargetEnvironmentRequest,
                       location: Location<*>,
                       failedTest: AbstractTestProxy): TargetEnvironmentFunction<String>? {
    val element = location.psiElement
    var pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
    if (location is PyPsiLocationWithFixedClass) {
      pyClass = location.fixedClass
    }
    val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
    val virtualFile = location.virtualFile
    return virtualFile?.canonicalPath?.let { localPath ->
      val targetPath = request.getTargetEnvironmentValueForLocalPath(localPath)
      (listOf(targetPath) + listOfNotNull(pyClass?.name, pyFunction?.name).map(::constant))
        .joinToStringFunction(separator = TEST_NAME_PARTS_SPLITTER)
    }
  }

  @Throws(RuntimeConfigurationException::class)
  override fun checkConfiguration() {
    super.checkConfiguration()
    if (requiredPackage != null && !isFrameworkInstalled()) {
      throw RuntimeConfigurationWarning(
        PyBundle.message("runcfg.testing.no.test.framework", requiredPackage))
    }
  }

  /**
   * Check if framework is available on SDK
   */
  fun isFrameworkInstalled(): Boolean {
    val sdk = sdk ?: return false // No SDK -- no tests
    val requiredPackage = this.requiredPackage ?: return true // Installed by default
    return PyPackageManager.getInstance(sdk).packages?.firstOrNull { it.name == requiredPackage } != null
  }


  companion object {
    /**
     * When passing path to test to runners, you should join parts with this char.
     * I.e.: file.py::PyClassTest::test_method
     */
    protected const val TEST_NAME_PARTS_SPLITTER = "::"
  }
}