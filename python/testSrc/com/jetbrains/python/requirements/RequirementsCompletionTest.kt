// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.packaging.repository.PyEmptyPackagePackageRepository
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.pythonSdk

class RequirementsCompletionTest : BasePlatformTestCase() {

  fun testPackageNameCompletion() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    myFixture.configureByFile("requirements/${getTestName(true)}.txt")
    myFixture.completeBasic()
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsElements(myFixture.lookupElementStrings!!, "mypy", "mypy-extensions")
  }

  fun testPackageNameInProjectTablePyProjectToml() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    myFixture.configureByFile("${getTestName(true)}/pyproject.toml")
    myFixture.completeBasic()
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsElements(myFixture.lookupElementStrings!!, "mypy", "mypy-extensions")
  }

  fun testPackageNameInBuildSystemTablePyProjectToml() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    myFixture.configureByFile("${getTestName(true)}/pyproject.toml")
    myFixture.completeBasic()
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsElements(myFixture.lookupElementStrings!!, "mypy", "mypy-extensions")
  }

  fun testVersionCompletion() {
    mockPackageDetails("mypy", listOf("1.7.0", "1.6.1", "1.6.0"))

    myFixture.configureByFile("requirements/${getTestName(true)}.txt")
    myFixture.completeBasic()
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsOrdered(myFixture.lookupElementStrings!!, "1.7.0", "1.6.1", "1.6.0")
  }

  fun testVersionInBuildSystemTablePyProjectToml() {
    mockPackageDetails("mypy", listOf("1.7.0", "1.6.1", "1.6.0"))

    myFixture.configureByFile("${getTestName(true)}/pyproject.toml")
    myFixture.completeBasic()
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsOrdered(myFixture.lookupElementStrings!!, "1.7.0", "1.6.1", "1.6.0")
  }

  fun testVersionInProjectTablePyProjectToml() {
    mockPackageDetails("mypy", listOf("1.7.0", "1.6.1", "1.6.0"))

    myFixture.configureByFile("${getTestName(true)}/pyproject.toml")
    myFixture.completeBasic()
    assertNotEmpty(myFixture.lookupElementStrings)
    assertContainsOrdered(myFixture.lookupElementStrings!!, "1.7.0", "1.6.1", "1.6.0")
  }

  private fun mockPackageNames(packageNames: List<String>) {
    val packageManagerProvider = TestPackageManagerProvider().withPackageNames(packageNames)
    ExtensionTestUtil.maskExtensions(PythonPackageManagerProvider.EP_NAME, listOf(packageManagerProvider), testRootDisposable)
  }

  private fun mockPackageDetails(packageName: String, availableVersions: List<String>) {
    val packageManagerProvider = TestPackageManagerProvider()
      .withPackageNames(listOf(packageName))
      .withPackageDetails(PythonSimplePackageDetails(packageName, availableVersions, PyEmptyPackagePackageRepository))
    ExtensionTestUtil.maskExtensions(PythonPackageManagerProvider.EP_NAME, listOf(packageManagerProvider), testRootDisposable)
  }

  override fun setUp() {
    super.setUp()
    myFixture.project.pythonSdk = projectDescriptor.sdk
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    val languageLevel = LanguageLevel.getLatest()
    return object : PyLightProjectDescriptor(languageLevel) {
      override fun getSdk(): Sdk {
        val sdk: Sdk = PythonMockSdk.create("Mock ${PyNames.PYTHON_SDK_ID_NAME} ${languageLevel.toPythonVersion()}",
                                            "${PythonTestUtil.getTestDataPath()}/MockSdk", PythonSdkType.getInstance(), languageLevel,
                                            *additionalRoots)
        (sdk as MockSdk).sdkAdditionalData = PythonSdkAdditionalData()
        return sdk
      }
    }
  }

  override fun getBasePath(): String {
    return "/community/python/testData/requirements/completion/"
  }
}