package com.jetbrains.python.testing.autoDetectTests

import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.PyUnitTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType

class PyAutoDetectionConfigurationFactoryTest : PyTestCase() {

  private lateinit var autoDetectFactory: PyAutoDetectionConfigurationFactory

  override fun setUp() {
    super.setUp()
    autoDetectFactory = PythonTestConfigurationType.getInstance().autoDetectFactory
  }

  fun testPytestDetected() {
    doTestAutoDetection(arrayOf("pytest"), PyTestFactory::class.java)
  }

  fun testUnitTestFallbackWhenNoPackages() {
    doTestAutoDetection(emptyArray(), PyUnitTestFactory::class.java)
  }

  fun testPytestPreferredOverNose() {
    doTestAutoDetection(arrayOf("nose", "pytest"), PyTestFactory::class.java)
  }

  private fun doTestAutoDetection(installedPackages: Array<String>, expectedFactoryClass: Class<out PyAbstractTestFactory<*>>) {
    val sdk = PythonSdkUtil.findPythonSdk(myFixture.module) ?: error("Python SDK not found")
    mockInstalledPackages(*installedPackages)
    val factory = autoDetectFactory.getFactory(sdk, myFixture.project)

    assertTrue(expectedFactoryClass.isInstance(factory))
  }

  private fun mockInstalledPackages(vararg packageNames: String) {
    val packages = packageNames.map { PythonPackage(it, "1.0.0", false) }
    val provider = TestPackageManagerProvider().withPackageInstalled(*packages.toTypedArray())
    ExtensionTestUtil.maskExtensions(
      PythonPackageManagerProvider.EP_NAME,
      listOf(provider),
      testRootDisposable
    )
  }
}
