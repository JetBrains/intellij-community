package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeBytes
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.run.PyRunConfigurationFactory
import com.jetbrains.python.run.PythonRunConfiguration
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Path
import  com.intellij.openapi.module.Module

@Internal
object TestUtils {
  @RequiresBackgroundThread
  fun getData(packagePath: String, fileName: String): ByteArray =
    javaClass.classLoader.getResource("$packagePath/$fileName")?.readBytes()
    ?: error("No file $packagePath/$fileName: broken installation?")


  /**
   * 1. Creates [fileName] in [baseDir] and file content there.
   * 2. Creates configuration if [mayBeUsedInRunConfiguration] and opens file.
   * Latter is done only if [module] is *not* `null`
   */
  private suspend fun createFile(
    baseDir: VirtualFile,
    packagePath: String,
    fileName: String,
    module: Module,
    mayBeUsedInRunConfiguration: Boolean = false,
    shouldOpenFile: Boolean = false,
  ): Pair<VirtualFile, PythonRunConfiguration?> {
    val templateContent = withContext(Dispatchers.IO + CoroutineName("Read data ${fileName}")) {
      getData(packagePath, fileName)
    }

    var runConfiguration: PythonRunConfiguration? = null

    val file = writeAction {
      // Create file and run configuration
      baseDir.createChildData(this, fileName).also { file ->
        file.writeBytes(templateContent)
        if (mayBeUsedInRunConfiguration) {
          runConfiguration = createRunConfiguration(file, module)
        }
      }
    }

    return withContext(Dispatchers.EDT + CoroutineName("Create ${fileName}")) {
      readAction {
        assertEquals(templateContent.decodeToString(), file.readText(), "Wrong data written to file")
        assertEquals(fileName, file.name, "Wrong file name")
      }

      if (shouldOpenFile) {
        // Open created file
        writeIntentReadAction { baseDir.refresh(false, true) }
        val psiFile = withContext(Dispatchers.IO) {
          readAction {
            PsiManager.getInstance(module.project).findFile(file)
          }
        }

        writeIntentReadAction { psiFile?.navigate(true) }
      }

      Pair(file, runConfiguration)
    }
  }

  @RequiresEdt
  @RequiresWriteLock
  private fun createRunConfiguration(pyFile: VirtualFile, module: Module): PythonRunConfiguration {
    val configuration = PyRunConfigurationFactory.getInstance()
      .createPythonScriptRunConfiguration(module, pyFile.path) as PythonRunConfiguration
    configuration.setShowCommandLineAfterwards(true)
    configuration.workingDirectory = pyFile.parent.path
    return configuration
  }

  fun getTestFile(packagePath: String, fileName: String, module: Module, baseDir: Path): VirtualFile = timeoutRunBlocking {
    val vfsBaseDir = LocalFileSystem.getInstance().findFileByNioFile(baseDir)!!
    createFile(vfsBaseDir, packagePath, fileName, module).first
  }
}