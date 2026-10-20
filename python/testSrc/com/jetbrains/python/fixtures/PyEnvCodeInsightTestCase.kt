// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration.Companion.minutes

/**
 * A [PyCodeInsightTestCase] on a real Python environment, for code insight on real packages.
 *
 * The test file is in a Python project of a real project, and the interpreter of the Python project is a virtual
 * environment of the env test Python. A subclass has the [PyEnvTestCase] annotation, which selects the environment.
 * It installs the packages under test with [installPackages] in its `@BeforeAll` method.
 * [TestCaseOptions.languageLevel] does not apply, because the environment gives the language level.
 */
abstract class PyEnvCodeInsightTestCase : PyCodeInsightTestCase() {

  override fun createFixture(testCaseOptions: TestCaseOptions): CodeInsightTestFixture {
    codeInsightFixture.testDataPath = PythonTestUtil.getTestDataPath()
    return codeInsightFixture
  }

  /** The JUnit 5 fixture framework tears the fixture down after the test class. */
  override fun disposeFixture(fixture: CodeInsightTestFixture) {
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val project by projectFixture
    private val projectDirFixture = tempPathFixture()
    private val pyProjectFixture = projectFixture.pyProjectFixture(projectDirFixture)

    // Not in the directory of the Python project: the test case deletes the files of that directory after each test.
    private val venv by pyEnvironmentFixture().pyVenvFixture(where = vfsCleanTempPathFixture(), pyProjectFixture = pyProjectFixture)
    private val codeInsightFixture by codeInsightFixture(projectFixture, projectDirFixture)

    /**
     * A [tempPathFixture] that deletes its files and refreshes the VFS for them at teardown.
     *
     * A venv with a large package has tens of thousands of files in the VFS. Without this refresh, the next test that
     * refreshes the local file system processes their removal, and the indexes of its project are not ready in time.
     */
    private fun vfsCleanTempPathFixture(): TestFixture<Path> = testFixture {
      val dir = tempPathFixture().init()
      initialized(dir) {
        withContext(Dispatchers.IO) {
          try {
            dir.listDirectoryEntries().forEach(NioFiles::deleteRecursively)
          }
          catch (e: IOException) {
            // The tempPathFixture tries the deletion again.
            fileLogger().warn("Cannot delete the files of $dir", e)
          }
          VfsUtil.markDirtyAndRefresh(false, true, true, dir)
        }
      }
    }

    /**
     * Installs each of [requirements] from PyPI into the virtual environment, for example `PyQt5==5.15.11`.
     * A failed install is tried again, up to three times.
     */
    @JvmStatic
    protected fun installPackages(vararg requirements: String): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      val packageManager = PythonPackageManager.forPythonInterpreter(project, venv)
      for (requirement in requirements) {
        val specification = PythonRepositoryPackageSpecification(PyPiPackageRepository, PyRequirementParser.fromLine(requirement)!!)
        val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(specification))
        var result = packageManager.installPackage(request)
        repeat(2) {
          if (!result.isSuccess) result = packageManager.installPackage(request)
        }
        assertTrue(result.isSuccess) { "Failed to install $requirement: ${result.errorOrNull}" }
      }
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
    }
  }
}
