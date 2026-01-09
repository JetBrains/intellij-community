package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.executeAction
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import com.intellij.util.io.createDirectories
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.writeText
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
  if (readAction { foldingModel.allFoldRegions.any { !it.isExpanded } }) {
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
private suspend fun doOpenFile(relativePath: String, project: Project) {
  val fileEditorManager = FileEditorManager.getInstance(project)
  val virtualFile = getVirtualFileByRelativePath(relativePath)
  writeAction {
    fileEditorManager.openFile(virtualFile, true)
  }
}

context(lambdaFrontendContext: LambdaFrontendContext)
private fun doOpenFile(relativePath: String, project: Project) {
  TODO() // can't use frontend module
}

context(lambdaBackendContext: LambdaBackendContext)
suspend fun createFile(relativePathString: String, fileContent: String? = null) {
  val project = getProject()
  val createdFile = writeIntentReadAction {
    frameworkLogger.info("Creating file at $relativePathString")

    val basePath = project.basePath ?: error("Project base path is null, can't resolve relative path for file creation")
    val projectBasePath = Path(basePath)

    frameworkLogger.info("Project base canonical path=${projectBasePath.pathString}")

    val fileToCreate = projectBasePath.resolve(relativePathString)
    assertThat(fileToCreate).doesNotExist()

    fileToCreate.parent.createDirectories()
    fileToCreate.findOrCreateFile()
  }
  waitSuspending("Wait until VFS is refreshed and file is resolved", timeout = 15.seconds) {
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path(createdFile.pathString)) != null
  }
  frameworkLogger.info("Successfully created file for relative path: $relativePathString, full path: ${createdFile.pathString}")
  if (fileContent != null) {
    writeIntentReadAction {
      createdFile.writeText(fileContent)
    }
  }
}

context(lambdaIdeContext: LambdaIdeContext)
fun assertNoOpenedFiles() {
  assertThat(
    FileEditorManager.getInstance(getProject()).openFiles
  ).isEmpty()
}