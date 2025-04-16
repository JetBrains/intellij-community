// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.TemplateFileName
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.mapResult
import com.jetbrains.python.projectCreation.createVenvAndSdk
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.milliseconds

internal val miscProjectDefaultPath: Lazy<Path> = lazy { Path.of(SystemProperties.getUserHome()).resolve("PyCharmMiscProject") }

/**
 * Creates a project in [projectPath] in a modal window.
 * Once created, uses [scopeProvider] to get scope
 * to launch [miscFileType] generation in background, returns it as a job.
 *
 * Pythons are obtained with [systemPythonService]
 */
@RequiresEdt
fun createMiscProject(
  miscFileType: MiscFileType,
  scopeProvider: (Project) -> CoroutineScope,
  confirmInstallation: suspend () -> Boolean,
  projectPath: Path = miscProjectDefaultPath.value,
  systemPythonService: SystemPythonService = SystemPythonService(),
): Result<Job, PyError> =
  runWithModalProgressBlocking(ModalTaskOwner.guess(),
                               PyCharmCommunityCustomizationBundle.message("misc.project.generating.env"),
                               TaskCancellation.cancellable()) {
    createProjectAndSdk(projectPath, confirmInstallation = confirmInstallation, systemPythonService = systemPythonService)
  }.mapResult { (project, sdk) ->
    Result.Success(scopeProvider(project).launch {
      withBackgroundProgress(project, PyCharmCommunityCustomizationBundle.message("misc.project.filling.file")) {
        generateAndOpenFile(projectPath, project, miscFileType, sdk)
      }
    })
  }


private suspend fun generateAndOpenFile(projectPath: Path, project: Project, fileType: MiscFileType, sdk: Sdk): PsiFile {
  val generateFile = generateFile(projectPath, fileType.fileName)
  val psiFile = openFile(project, generateFile)
  fileType.fillFile(psiFile, sdk)
  return psiFile
}

private suspend fun openFile(project: Project, file: Path): PsiFile {
  val vfsFile = withContext(Dispatchers.IO) {
    VfsUtil.findFile(file, true) ?: error("Can't find VFS $file")
  }
  // `Navigate` throws `AssertionError` from time to time due to a platform API bug.
  // We "fix" it by retries
  return callWithRetry {
    withContext(Dispatchers.EDT) {
      val psiFile = readAction { PsiManager.getInstance(project).findFile(vfsFile) } ?: error("Can't find PSI for $vfsFile")
      psiFile.navigate(true)

      return@withContext psiFile
    }
  }
}

/**
 * Retries  [code] `10` times if it throws [AssertionError]
 */
private suspend fun <T> callWithRetry(code: suspend () -> T): T {
  val logger = fileLogger()
  repeat(10) {
    try {
      return code()
    }
    catch (e: AssertionError) {
      logger.warn(e)
      delay(100.milliseconds)
    }
  }
  return code()
}

private suspend fun generateFile(where: Path, templateFileName: TemplateFileName): Path = withContext(Dispatchers.IO) {
  repeat(Int.MAX_VALUE) {
    val file = where.resolve(templateFileName.nameWithSuffix(it))
    try {
      file.createFile()
      return@withContext file
    }
    catch (_: FileAlreadyExistsException) {
    }
  }
  error("Too many files in $where")
}


/**
 * Creates a project with one module in [projectPath] and sdk using the highest python.
 * Pythons are searched using [systemPythonService].
 * If no Python found and [confirmInstallation] we install it using [SystemPythonService.getInstaller]
 */
private suspend fun createProjectAndSdk(
  projectPath: Path,
  confirmInstallation: suspend () -> Boolean,
  systemPythonService: SystemPythonService,
): Result<Pair<Project, Sdk>, PyError> {
  val vfsProjectPath = createProjectDir(projectPath).getOr { return it }
  val project = openProject(projectPath)
  val sdk = createVenvAndSdk(project, confirmInstallation, systemPythonService, vfsProjectPath).getOr { return it }
  return Result.success(Pair(project, sdk))
}


private suspend fun openProject(projectPath: Path): Project {
  TrustedProjects.setProjectTrusted(projectPath, true)
  val projectManager = ProjectManagerEx.getInstanceEx()
  val project = projectManager.openProjectAsync(projectPath, OpenProjectTask {
    runConfigurators = false
    isProjectCreatedWithWizard = true

  }) ?: error("Failed to open project in $projectPath, check logs")
  // There are countless numbers of reasons `openProjectAsync` might return null

  return project
}


/**
 * Creating a project != creating a directory for it, but we need a directory to create a template file
 */
private suspend fun createProjectDir(projectPath: Path): Result<VirtualFile, PyError.Message> = withContext(Dispatchers.IO) {
  try {
    projectPath.createDirectories()
  }
  catch (e: IOException) {
    thisLogger().warn("Couldn't create $projectPath", e)
    return@withContext failure(
      PyCharmCommunityCustomizationBundle.message("misc.project.error.create.dir", projectPath, e.localizedMessage))
  }
  val projectPathVfs = VfsUtil.findFile(projectPath, true)
                       ?: error("Can't find VFS $projectPath")
  return@withContext Result.Success(projectPathVfs)
}
