package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.python.featuresTrainer.ift.PythonLangSupport
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.DEFAULT_VIRTUALENV_DIRNAME
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import training.learn.NewLearnProjectUtil
import training.project.ProjectUtils
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class PythonLangSupportTest {
  companion object {
    @JvmStatic
    @TempDir
    lateinit var temporarySystemPath: Path

    @JvmStatic
    @BeforeAll
    fun setUp() {
      ProjectUtils.customSystemPath = temporarySystemPath
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      ProjectUtils.customSystemPath = null
    }
  }


  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun ensureVenvCreatedTest(venvAlreadyExists: Boolean, @PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(10.minutes) {
    val learningProjectsPath = ProjectUtils.learningProjectsPath
    assert(learningProjectsPath.startsWith(temporarySystemPath)) { "$learningProjectsPath must reside in $temporarySystemPath" }

    val sut = PythonLangSupport(ErrorSink {
      Assertions.fail(it.message)
    })

    if (venvAlreadyExists) {
      val venvPath = learningProjectsPath.resolve(DEFAULT_VIRTUALENV_DIRNAME)
      createVenv(python, venvPath).orThrow()
    }

    val sema = CompletableDeferred<Project>()
    withContext(Dispatchers.EDT) {

      NewLearnProjectUtil.createLearnProject(null, sut, null) { project ->
        sema.complete(project)
      }
    }
    val project = sema.await()
    withContext(Dispatchers.IO) {
      sut.cleanupBeforeLessons(project)
    }
    val sdk = project.pythonSdk!!
    try {
      val pythonBinary = Path.of(sdk.homePath!!)
      Assertions.assertTrue(PythonWithLanguageLevelImpl.createByPythonBinary(pythonBinary).orThrow().languageLevel.isPy3K, "Sdk is broken")
    }
    finally {
      writeAction {
        ProjectJdkTable.getInstance().removeJdk(sdk)
      }
      withContext(Dispatchers.EDT) {
        ProjectManager.getInstance().closeAndDispose(project)
      }
      withContext(Dispatchers.IO) {
        if (SystemInfoRt.isWindows) {
          deleteCheckLocking(learningProjectsPath) // drop dir if some leaked python project keeps it
        }
      }
    }
  }
}