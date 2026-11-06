package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.requirements.pyRequirementVersionSpec
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.management.getInstalledPackage
import com.jetbrains.python.packaging.management.hasInstalledPackage
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@PyEnvTestCaseWithConda
internal class CondaPackageManagerTest {
  private val projectFixture = projectFixture()

  private lateinit var packageManager: PythonPackageManager
  private var currentPackages: List<PythonPackage> = listOf()
  private lateinit var condaSdk: Sdk

  @BeforeEach
  fun setUp(@CondaEnv condaEnv: PyCondaEnv): Unit = timeoutRunBlocking {
    condaSdk = timeoutRunBlocking(230.seconds) {
      condaEnv.createSdkFromThisEnv(projectFixture.get(), null, projectFixture.get().basePath?.toNioPathOrNull()!!).getOrThrow().getSdkAPI()
    }
    assertTrue(condaSdk.pySdkAdditionalData.flavor is CondaEnvSdkFlavor, "$condaSdk must be a conda SDK")
    val interpreter = condaSdk.pythonInterpreterResultAsync().orThrow()
    assertNotNull(interpreter.getPythonInfo().successOrNull, "$condaSdk must be usable")
    packageManager = CondaPackageManager(projectFixture.get(), interpreter).also {
      Disposer.register(projectFixture.get(), it)
    }

  }

  // PY-89222: versions are pinned on purpose. Asking for "anything newer" made the update transaction
  // install whatever a fresh release dragged along -- pytest 8.4 added a pygments dependency -- and
  // materializing a package that is not in the agent-wide conda package cache yet trips over corrupted
  // or cross-drive entries there. Conda then exits with a non-zero code and the version simply stays put.
  // Both pairs below share a single dependency set, so an update installs nothing but the package itself.
  @Test
  fun testInstallMixedCondaPypiPackages() {
    checkInstallPackage(
      UpdateCase(CondaPackage("pytest", "7.4.0", false), updatedVersion = "7.4.4"),
      UpdateCase(PythonPackage("yandex-book-api", "0.1.0", false), updatedVersion = "0.1.2"),
    )
  }

  // PY-91412: a version constraint like "pytest<8.0" contains '<', one of the characters conda's docs say to
  // quote on a shell command line. The IDE passes the spec to conda as a single argv element (no shell), so it
  // must arrive verbatim. A stray leading quote used to corrupt it into `"pytest<8.0`, which conda 26.x rejects
  // with InvalidMatchSpec.
  @Test
  fun testInstallCondaPackageWithVersionConstraint(): Unit = timeoutRunBlocking(800.seconds) {
    packageManager.waitForInit()

    val spec = packageManager.findPackageSpecification("pytest", "8.0", PyRequirementRelation.LT)
               ?: error("Cannot create package specification for pytest")
    // Precondition: the spec really carries the '<' that used to be corrupted.
    assertTrue(spec.nameWithVersionSpecs.contains('<'), "Expected a '<' constraint, got '${spec.nameWithVersionSpecs}'")

    val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))
    packageManager.installPackage(request).getOrThrow()

    val installed = packageManager.getInstalledPackage("pytest") ?: throw AssertionError("pytest was not installed")
    currentPackages = listOf(installed)

    val major = installed.version.substringBefore('.').toIntOrNull()
                ?: throw AssertionError("Unexpected pytest version '${installed.version}'")
    assertTrue(major < 8, "Constraint '<8.0' was not honored: installed pytest ${installed.version}")
  }

  @AfterEach
  fun cleanUp() {
    timeoutRunBlocking(600.seconds) {
      removeTestSdkFromOpenProjects(condaSdk)
      for (pkg in currentPackages) {
        packageManager.uninstallPackage(pkg.name)
      }
    }
  }


  /** A package to install at [initial]'s version, then update to [updatedVersion]. */
  private data class UpdateCase(val initial: PythonPackage, val updatedVersion: String)

  private fun checkInstallPackage(vararg cases: UpdateCase) {
    val packages = cases.map { it.initial }.toTypedArray()
    currentPackages = packages.toList()
    installPackagesAndCheck(*packages)
    updatePackagesAndCheck(*cases)
    removePackagesAndCheck(*packages)
  }

  private fun installPackagesAndCheck(vararg packages: PythonPackage) = timeoutRunBlocking(800.seconds) {
    packageManager.waitForInit()
    val specs = packages.map {
      val versionSpec = pyRequirementVersionSpec(PyRequirementRelation.EQ, it.version)
      packageManager.findPackageSpecification(it.name, versionSpec)
      ?: error("Cannot create package specification for ${it.name} ${it.version}")
    }
    val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specs)
    packageManager.installPackage(installRequest).getOrThrow()

    for (pkg in packages) {
      if (!packageManager.hasInstalledPackage(pkg)) {
        throw AssertionError("Package ${pkg.name} was not installed")
      }
    }
  }

  private fun updatePackagesAndCheck(vararg cases: UpdateCase) = timeoutRunBlocking(800.seconds) {
    packageManager.waitForInit()

    val specs = cases.map {
      val versionSpec = pyRequirementVersionSpec(PyRequirementRelation.EQ, it.updatedVersion)
      packageManager.findPackageSpecification(it.initial.name, versionSpec)
      ?: error("Cannot create package specification for ${it.initial.name} ${it.updatedVersion}")
    }
    packageManager.updatePackages(*specs.toTypedArray()).getOrThrow()

    for (case in cases) {
      val installed = packageManager.getInstalledPackage(case.initial.name)
                      ?: throw AssertionError("Package ${case.initial.name} is not installed after the update")
      if (installed.version != case.updatedVersion) {
        throw AssertionError("Package ${case.initial.name} was not updated to ${case.updatedVersion}, got ${installed.version}")
      }
    }
  }

  private fun removePackagesAndCheck(vararg packages: PythonPackage) = timeoutRunBlocking(800.seconds) {
    packageManager.waitForInit()
    packageManager.uninstallPackage(*packages.map { it.name }.toTypedArray())

    for (pkg in packages) {
      if (packageManager.hasInstalledPackage(pkg.name)) {
        throw AssertionError("Package ${pkg.name} was not deleted")
      }
    }
  }

}