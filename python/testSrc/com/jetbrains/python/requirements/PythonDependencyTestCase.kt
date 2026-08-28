// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PyNames
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageRepository
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.pythonSdk

abstract class PythonDependencyTestCase : BasePlatformTestCase() {

  protected fun initTestPackageManager(provider: TestPackageManagerProvider) {
    ExtensionTestUtil.maskExtensions(PythonPackageManagerProvider.EP_NAME, listOf(provider), testRootDisposable)
  }

  protected fun mockPackageNames(packageNames: List<String>) {
    val packageManagerProvider = TestPackageManagerProvider().withPackageNames(packageNames)
    initTestPackageManager(packageManagerProvider)
  }

  protected fun mockPackageDetails(packageName: String, availableVersions: List<String>) {
    val packageManagerProvider = TestPackageManagerProvider()
      .withPackageNames(listOf(packageName))
      .withPackageDetails(PythonSimplePackageDetails(packageName, availableVersions, TestPackageRepository(emptySet())))
    initTestPackageManager(packageManagerProvider)
  }

  protected fun checkCompletionResults(vararg expected: String) {
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsElements(myFixture.lookupElementStrings!!, *expected)
  }

  protected fun checkCompletionResultsOrdered(vararg expected: String) {
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsOrdered(myFixture.lookupElementStrings!!, *expected)
  }

  protected fun completeInTomlFile() {
    myFixture.configureByFile("${getTestName(true)}/pyproject.toml")
    myFixture.completeBasic()
  }

  protected fun completeInRequirementsFile() {
    myFixture.configureByFile("requirements/${getTestName(true)}.txt")
    myFixture.completeBasic()
  }

  override fun setUp() {
    super.setUp()
    myFixture.project.pythonSdk = projectDescriptor.sdk
  }

  final override fun getProjectDescriptor(): LightProjectDescriptor {
    val languageLevel = LanguageLevel.getLatest()
    return object : PyLightProjectDescriptor(this@PythonDependencyTestCase.name, languageLevel) {
      override fun getSdk(): Sdk =
        PythonMockSdk.create("Mock ${PyNames.PYTHON_SDK_ID_NAME} ${languageLevel.toPythonVersion()}",
                             "${PythonTestUtil.getTestDataPath()}/MockSdk", PythonSdkType.getInstance(), languageLevel)
    }
  }


  override fun getBasePath(): String = "/community/python/testData/requirements/"

}