package com.jetbrains.python.testing.autoDetectTests

import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.fixtures.findPythonInterpreterForTests
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.PyUnitTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType

@Subsystems.TestRunner
@Components.Unittest
@Layers.Functional
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
    val interpreter = findPythonInterpreterForTests(myFixture.module) ?: error("No interpreter for ${myFixture.module.name}")
    mockInstalledPackages(*installedPackages)
    // The factory API takes an SDK.
    @Suppress("DEPRECATION")
    val factory = autoDetectFactory.getFactory(interpreter.getSdkAPI(), myFixture.project)

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
