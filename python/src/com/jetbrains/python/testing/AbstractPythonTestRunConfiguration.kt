// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.Location
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.extensions.getQName
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration.Companion.TEST_NAME_PARTS_SPLITTER
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Parent of all test configurations
 *
 * @author Ilya.Kazakevich
 */
abstract class AbstractPythonTestRunConfiguration<T : AbstractPythonTestRunConfiguration<T>>
@JvmOverloads
protected constructor(project: Project, factory: ConfigurationFactory, private val requiredPackage: String? = null) :
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

  open fun getTestSpec(
    request: TargetEnvironmentRequest,
    location: Location<*>,
    failedTest: AbstractTestProxy,
  ): TargetEnvironmentFunction<String>? {
    val element = location.psiElement
    var pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
    if (location is PyPsiLocationWithFixedClass) {
      pyClass = location.fixedClass
    }
    val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
    val virtualFile = location.virtualFile ?: return null

    return createTargetEnvFunction(virtualFile, pyClass?.name, pyFunction?.name)
  }

  /**
   * Creates a target environment function based on the provided parameters.
   *
   * @param virtualFile The [virtualFile] representing the Python file.
   * @param className The name of the class (can be null).
   * @param funName The name of the function (can be null).
   * @return The created target environment function, or null if it is impossible to compute the testSpec.
   */
  @Internal
  protected fun createTargetEnvFun(virtualFile: VirtualFile, className: String?, funName: String?, namesSplitter: String = TEST_NAME_PARTS_SPLITTER): TargetEnvironmentFunction<String>? {
    val testSpec = ReadAction.compute<String?, IllegalStateException> {
      val pythonFile = PsiManager.getInstance(project).findFile(virtualFile) as? PyFile ?: return@compute null
      val qName = pythonFile.getQName() ?: return@compute null
      (listOf(qName) + listOfNotNull(className, funName)).joinToString(namesSplitter)
    } ?: return null

    return TargetEnvironmentFunction {
      testSpec
    }
  }

  @Internal
  protected open fun createTargetEnvFunction(virtualFile: VirtualFile, className: String?, funName: String?): TargetEnvironmentFunction<String>? =
    createTargetEnvFun(virtualFile, className, funName)

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
    val sdk = sdk
    if (sdk == null) {
      // No SDK -- no tests
      logger<AbstractPythonRunConfiguration<*>>().warn("Failed to detect test framework: SDK is null")
      return false
    }
    val requiredPackage = this.requiredPackage ?: return true // Installed by default
    val isInstalled = runBlockingMaybeCancellable {
      PythonPackageManager.forSdk(project, sdk).hasInstalledPackage(requiredPackage)
    }
    return isInstalled
  }


  companion object {
    /**
     * When passing path to test to runners, you should join parts with this char.
     * I.e.: file.py::PyClassTest::test_method
     */
    protected const val TEST_NAME_PARTS_SPLITTER = "::"
  }
}