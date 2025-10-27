package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.executeAction
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.utils.waitSuspending
import com.intellij.lambda.testFramework.testApi.utils.waitSuspendingNotNull
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

context(lambdaIdeContext: LambdaIdeContext)
fun fileByRelativePathExists(relativePath: String): Boolean =
  getVirtualFileOrNullByRelativePath(relativePath) != null

context(lambdaIdeContext: LambdaIdeContext)
fun getVirtualFileOrNullByRelativePath(relativePath: String): VirtualFile? {
  val basePath = getProject().basePath ?: error("nullable project's base path")
  val file = File(basePath).resolve(relativePath)
  return getVirtualFileOrNull(file)
}

context(lambdaIdeContext: LambdaIdeContext)
fun getVirtualFileByRelativePath(relativePath: String): VirtualFile =
  getVirtualFileOrNullByRelativePath(relativePath) ?: error("cannot find virtual file by path $relativePath")

context(lambdaIdeContext: LambdaIdeContext)
fun getVirtualFileOrNull(file: File): VirtualFile? =
  VfsUtil.findFileByIoFile(file, /* refreshIfNeeded = */ true)

context(lambdaIdeContext: LambdaIdeContext)
fun getVirtualFile(file: File): VirtualFile =
  getVirtualFileOrNull(file) ?: error("cannot find virtual file by path ${file.path}")

context(lambdaIdeContext: LambdaIdeContext)
private fun defaultRequireFocus() =
  (lambdaIdeContext is LambdaFrontendContext) && !ApplicationManager.getApplication().isHeadlessEnvironment

context(lambdaIdeContext: LambdaIdeContext)
suspend fun withCurrentFile(
  project: Project = getProject(),
  waitForReadyState: Boolean = true,
  requireFocus: Boolean = defaultRequireFocus(),
  block: suspend EditorImpl.() -> Unit = {},
): FileEditor =
  project.selectedFileEditorOrThrow.apply {
    if (requireFocus) {
      waitForFocus()
    }
    editorImplOrThrow.let {
      if (waitForReadyState) {
        it.waitEditorIsLoaded()
        waitForCodeAnalysisToFinish {
          it.waitTrafficLineRenderReady()
        }
      }
      it.block()
    }
  }

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForExpectedOpenedFile(
  fileName: String,
  expectedOffset: Int? = null,
  project: Project = getProject(),
  timeout: Duration = 20.seconds,
): FileEditor {
  val fileEditor = waitSuspendingNotNull("Editor '$fileName' is opened", timeout) {
    project.allOpenFileEditors.find { it.file?.name == fileName }
  }

  expectedOffset?.let {
    fileEditor.editorImplOrThrow.waitCaretOffset(it)
  }
  return fileEditor
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitNoOpenedFile(
  project: Project = getProject(),
  timeout: Duration = 20.seconds,
) {
  waitSuspendingNotNull("No editors are opened", timeout) {
    project.allOpenFileEditors.isEmpty()
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForExpectedSelectedFile(
  fileName: String,
  expectedOffset: Int? = null,
  checkFocus: Boolean = defaultRequireFocus(),
  project: Project = getProject(),
  timeout: Duration = 20.seconds,
): FileEditor {
  val fileEditor = waitSuspending("Editor '$fileName' is selected in project $project",
                                  timeout,
                                  getter = { project.selectedFileEditor },
                                  checker = {
                                    frameworkLogger.info("file=${it?.file}, fileName=${it?.file?.name}")
                                    it?.file?.name == fileName
                                  })!!
  if (checkFocus) {
    fileEditor.waitForFocus()
  }

  expectedOffset?.let {
    fileEditor.editorImplOrThrow.waitCaretOffset(it)
  }
  return fileEditor
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForExpectedSelectedFile(
  fileName: String,
  expectedLine: Int,
  expectedColumn: Int,
  checkFocus: Boolean = defaultRequireFocus(),
  timeout: Duration = 20.seconds,
): FileEditor {
  val fileEditor = waitForExpectedSelectedFile(fileName = fileName, expectedOffset = null, checkFocus = checkFocus,
                                               timeout = timeout).also {
    it.editorImplOrThrow.waitCaretPosition(expectedLine, expectedColumn)
  }
  return fileEditor
}

/**
 * `waitForReadyState` also includes waiting the indexes to finish, so if they are not needed or unavailable, you need to set `waitForReadyState` to `false`
 * because otherwise it will fail with timeout
 */
context(lambdaIdeContext: LambdaIdeContext)
suspend fun <T> withOpenedFile(
  relativePath: String,
  project: Project = getProject(),
  forceExpandAllRegions: Boolean = true,
  waitForReadyState: Boolean = true,
  requireFocus: Boolean = defaultRequireFocus(),
  block: suspend EditorImpl.() -> T,
): T =
  openFile(relativePath, project, forceExpandAllRegions, waitForReadyState, requireFocus).editorImplOrThrow.block()

context(lambdaIdeContext: LambdaIdeContext)
suspend fun openFile(
  relativePath: String,
  project: Project = getProject(),
  forceExpandAllRegions: Boolean = true,
  waitForReadyState: Boolean = true,
  requireFocus: Boolean = defaultRequireFocus(),
): FileEditor {
  frameworkLogger.info("Open file $relativePath, forceExpandAllRegions=$forceExpandAllRegions, " +
                       "waitForReadyState=$waitForReadyState")
  val fileName = Path.of(relativePath).fileName.name
  val oldFileEditor = project.selectedFileEditor
  // we are checking by the file name, so something could go wrong for two files with the same name.
  val fileEditor =
    if (oldFileEditor?.file?.name == Path.of(relativePath).fileName.name) {
      frameworkLogger.info("File with name '${oldFileEditor.file?.name}' is already opened")
      if (requireFocus) {
        oldFileEditor.waitForFocus()
      }
      oldFileEditor
    }
    else {
      when (lambdaIdeContext) { //https://youtrack.jetbrains.com/issue/KT-58770/False-positive-Overload-resolution-ambiguity-with-same-named-extension-function-and-lambda-as-parameter
        is LambdaBackendContext -> doOpenFile(relativePath, project) // backend should be first for monolith runs
        is LambdaFrontendContext -> doOpenFile(relativePath, project)
        else -> error("unexpected context")
      }
      waitForExpectedSelectedFile(fileName, checkFocus = requireFocus, project = project)
    }

  fileEditor.editorImpl?.let {
    if (waitForReadyState) {
      it.waitEditorIsLoaded()
      waitForCodeAnalysisToFinish {
        it.waitTrafficLineRenderReady()
      }
    }
    if (forceExpandAllRegions) {
      it.expandAllRegionsIfAny(waitForReadyState)
    }
  }
  return fileEditor
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.expandAllRegionsIfAny(waitForReadyState: Boolean = true) {
  if (foldingModel.allFoldRegions.any { !it.isExpanded }) {
    frameworkLogger.info("Expand all regions in the file ${fileName}")
    executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS, dataContext)
    if (waitForReadyState) { // analysis will be restarted on command execution
      waitForCodeAnalysisToFinish {
        waitTrafficLineRenderReady()
      }
    }
  }
  else {
    frameworkLogger.info("No regions to expand in the file ${fileName}")
  }
}

context(lambdaBackendContext: LambdaBackendContext)
fun closeFile(relativePath: String, project: Project = getProject()) {
  frameworkLogger.info("Find and close file with path '$relativePath'")

  val virtualFile = getVirtualFileByRelativePath(relativePath)
  closeFile(virtualFile, project)
}

context(lambdaIdeContext: LambdaIdeContext)
fun closeFile(virtualFile: VirtualFile, project: Project = getProject()) {
  frameworkLogger.info("Close file if open '${virtualFile.path}'")

  val fileEditorManager = FileEditorManager.getInstance(project)

  if (!fileEditorManager.isFileOpen(virtualFile)) {
    frameworkLogger.info("File is not open to be closed '${virtualFile.name}'")
    return
  }

  frameworkLogger.info("Closing the file ${virtualFile.name}")
  FileEditorManager.getInstance(project).closeFile(virtualFile)

  assert(!fileEditorManager.isFileOpen(virtualFile)) { "File is still open '${virtualFile.name}'" }
}

context(lambdaBackendContext: LambdaBackendContext)
private fun doOpenFile(relativePath: String, project: Project) {
  val fileEditorManager = FileEditorManager.getInstance(project)
  val virtualFile = getVirtualFileByRelativePath(relativePath)
  fileEditorManager.openFile(virtualFile, true)
}

context(lambdaFrontendContext: LambdaFrontendContext)
private fun doOpenFile(relativePath: String, project: Project) {
  TODO() // can't use frontend module
}

context(lambdaBackendContext: LambdaBackendContext)
fun createFile(relativePath: String) {
  val project = getProject()
  val command = Runnable {
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        frameworkLogger.info("Creating file at $relativePath")
        val basePath = project.basePath ?: error("Project base path is null, can't resolve relative path for file creation")
        val projectBaseFile = File(basePath)

        frameworkLogger.info("Project base canonical path=${projectBaseFile.canonicalPath}")

        val fileToCreate = projectBaseFile.resolve(relativePath)

        val directoriesToCreate = mutableListOf<File>()
        var parentDir = fileToCreate.parentFile

        frameworkLogger.info("Checking whether we need to create parent directories")
        while (parentDir.canonicalPath != projectBaseFile.canonicalPath) {
          val vfsParentDir = getVirtualFileOrNullByRelativePath(parentDir.path)
          if (vfsParentDir?.exists() == true) {
            if (vfsParentDir.isDirectory) {
              frameworkLogger.info("Found directory at ${vfsParentDir.canonicalPath}")
              break
            }
            error("Failed to create file at $relativePath because ${parentDir.canonicalPath} already exists and is not a directory.")
          }

          directoriesToCreate.add(parentDir)
          parentDir = parentDir.parentFile
        }

        fun File.getParentVfsFile() = getVirtualFileOrNull(this.parentFile)
                                      ?: error("Cannot find directory by path ${this.parentFile}")

        directoriesToCreate.reversed().forEach {
          frameworkLogger.info("Creating ${it}")
          it.getParentVfsFile().createChildDirectory(this, it.name)
        }

        frameworkLogger.info("Creating file: $relativePath")
        fileToCreate.getParentVfsFile().createChildData(this, fileToCreate.name)
        getVirtualFileOrNull(fileToCreate) ?: error("Can't see file after creating it in VFS")
        frameworkLogger.info("Successfully created file: $relativePath")
      }
    })
  }
  CommandProcessor.getInstance().executeCommand(project, command, "createFile", null)
}

context(lambdaIdeContext: LambdaIdeContext)
fun assertNoOpenedFiles() {
  assertThat(
    FileEditorManager.getInstance(getProject()).openFiles
  ).isEmpty()
}