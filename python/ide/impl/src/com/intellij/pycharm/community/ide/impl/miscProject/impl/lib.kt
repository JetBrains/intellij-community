// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenMode
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.TemplateFileName
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pyproject.model.evolution.findMainPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.mapResult
import com.jetbrains.python.projectCreation.SystemPythonRequirements
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.withSdkConfigurationLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

internal val miscProjectDefaultPath: Path
  get() = Path.of(ProjectUtil.getBaseDir()).resolve("PyCharmHome")

/**
 * Creates a project in [projectPath] in a modal window.
 * Once created, uses [scopeProvider] to get scope
 * to launch [miscFileType] generation in background, returns it as a job.
 *
 * Pythons are obtained with [systemPythonService]
 */
@ApiStatus.Internal
suspend fun createMiscProject(
  miscFileType: MiscFileType,
  scopeProvider: (Project) -> CoroutineScope,
  confirmInstallation: suspend () -> Boolean,
  projectPath: Path = miscProjectDefaultPath,
  systemPythonService: SystemPythonService = SystemPythonService(),
  currentProject: Project? = null,
): PyResult<Job> {
  return createOrOpenProjectAndSdk(
    projectPath,
    confirmInstallation = confirmInstallation,
    systemPythonService = systemPythonService,
    currentProject = currentProject,
  ).mapResult { (project, sdk) ->
    Result.Success(scopeProvider(project).launch {
      withBackgroundProgress(project, PyCharmCommunityCustomizationBundle.message("misc.project.filling.file")) {
        generateAndOpenFile(projectPath, project, miscFileType, sdk)
      }
    })
  }
}

@ApiStatus.Internal
suspend fun createMiscProject(
  confirmInstallation: suspend () -> Boolean,
  projectPath: Path = miscProjectDefaultPath,
  systemPythonService: SystemPythonService = SystemPythonService(),
  currentProject: Project? = null,
): PyResult<Pair<Project, PythonInterpreter>> {
  return createOrOpenProjectAndSdk(
    projectPath,
    confirmInstallation = confirmInstallation,
    systemPythonService = systemPythonService,
    currentProject = currentProject,
  )
}

private suspend fun generateAndOpenFile(projectPath: Path, project: Project, fileType: MiscFileType, pythonInterpreter: PythonInterpreter): PsiFile {
  val generateFile = generateFile(projectPath, fileType.fileName)
  val psiFile = openFile(project, generateFile)
  fileType.fillFile(psiFile, pythonInterpreter)
  return psiFile
}

private suspend fun openFile(project: Project, file: Path): PsiFile {
  val vfsFile = withContext(Dispatchers.IO) {
    VfsUtil.findFile(file, true) ?: error("Can't find VFS $file")
  }
  val psiFile = readAction {
    PsiManager.getInstance(project).findFile(vfsFile)
  } ?: error("Can't find PSI for $vfsFile")

  FileEditorManager.getInstanceAsync(project).openFile(
    vfsFile,
    FileEditorOpenRequest.withFocus(true).withOpenMode(FileEditorOpenMode.DEFAULT),
  )
  return psiFile
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
private suspend fun createOrOpenProjectAndSdk(
  projectPath: Path,
  confirmInstallation: suspend () -> Boolean,
  systemPythonService: SystemPythonService,
  currentProject: Project?,
): PyResult<Pair<Project, PythonInterpreter>> {
  val isAlreadyMiscOrWelcomeScreenProject = currentProject != null && WelcomeScreenProjectProvider.isWelcomeScreenProject(currentProject)
  val project = if (isAlreadyMiscOrWelcomeScreenProject) {
    currentProject
  }
  else {
    openProject(projectPath)
  }

  val existingMainInterpreter = project.findMainPythonInterpreter()
  if (isAlreadyMiscOrWelcomeScreenProject && existingMainInterpreter != null) {
    return PyResult.success(project to existingMainInterpreter)
  }

  val vfsProjectPath = createProjectDir(projectPath).getOr { return it }
  // Even if the misc project might be already opened, it might not have sdk (if it was opened as a welcome project)
  // A background progress, not a modal one: the interpreter waits for the Python project model, and a modal progress would
  // hold back each write that the model needs.
  val sdkResult = withSdkConfigurationLock(project) {
    val systemPythonRequirements = SystemPythonRequirements.ByVersionSpecifier(systemPythonService, confirmInstallation = confirmInstallation)
    createVenvAndSdk(ModuleOrProject.ProjectOnly(project), systemPythonRequirements, vfsProjectPath)
  }
  val sdk = sdkResult.getOr(PyBundle.message("project.error.cant.venv")) { return it }
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
private suspend fun createProjectDir(projectPath: Path): Result<VirtualFile, MessageError> = withContext(Dispatchers.IO) {
  try {
    projectPath.createDirectories()
  }
  catch (e: IOException) {
    thisLogger().warn("Couldn't create $projectPath", e)
    return@withContext PyResult.localizedError(
      PyCharmCommunityCustomizationBundle.message("misc.project.error.create.dir", projectPath, e.localizedMessage))
  }
  val projectPathVfs = VfsUtil.findFile(projectPath, true)
                       ?: error("Can't find VFS $projectPath")
  return@withContext Result.Success(projectPathVfs)
}
