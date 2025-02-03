// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.experiment.ab.impl.experiment.ABExperiment
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.TemplateFileName
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.*
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.milliseconds

private val logger = fileLogger()

internal val miscProjectDefaultPath: Lazy<Path> = lazy { Path.of(SystemProperties.getUserHome()).resolve("PyCharmMiscProject") }
internal val miscProjectEnabled: Lazy<Boolean> = lazy { ABExperiment.getABExperimentInstance().isExperimentOptionEnabled(PyMiscProjectExperimentOption::class.java) }

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
): Result<Job, @Nls String> =
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
): Result<Pair<Project, Sdk>, @Nls String> {
  val projectPathVfs = createProjectDir(projectPath).getOr { return it }
  val venvDirPath = projectPath.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)

  // Find venv in a project
  var venvPython: PythonBinary? = findExistingVenv(venvDirPath)

  if (venvPython == null) {
    // No venv found -- find system python to create venv
    val systemPythonBinary = getSystemPython(confirmInstallation = confirmInstallation, systemPythonService).getOr { return it }
    logger.info("no venv in $venvDirPath, using system python $systemPythonBinary to create venv")
    // create venv using this system python
    venvPython = createVenv(systemPythonBinary, venvDir = venvDirPath).getOr {
      return Result.failure(PyCharmCommunityCustomizationBundle.message("misc.project.error.create.venv", it.error.message, venvDirPath))
    }
  }

  logger.info("using venv python $venvPython")
  val project = openProject(projectPath)
  val sdk = getSdk(venvPython, project)
  val module = project.modules.first()
  ensureModuleHasRoot(module, projectPathVfs)
  ModuleRootModificationUtil.setModuleSdk(module, sdk)
  return Result.Success(Pair(project, sdk))
}

/**
 * Search for existing venv in [venvDirPath] and make sure it is usable.
 * `null` means no venv or venv is broken (it doesn't report its version)
 */
private suspend fun findExistingVenv(
  venvDirPath: Path,
): PythonBinary? = withContext(Dispatchers.IO) {
  val pythonPath = VirtualEnvReader.Instance.findPythonInPythonRoot(venvDirPath) ?: return@withContext null
  val flavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(pythonPath.toString())
  if (flavor == null) {
    logger.warn("No flavor found for $pythonPath")
    return@withContext null
  }
  return@withContext when (val p = pythonPath.validatePythonAndGetVersion()) {
    is Result.Success -> pythonPath
    is Result.Failure -> {
      logger.warn("No version string. python seems to be broken: $pythonPath. ${p.error}")
      null
    }
  }
}


private suspend fun getSystemPython(confirmInstallation: suspend () -> Boolean, pythonService: SystemPythonService): Result<PythonBinary, @Nls String> {


  // First, find the latest python according to strategy
  var systemPythonBinary = pythonService.findSystemPythons().firstOrNull()

  // No python found?
  if (systemPythonBinary == null) {
    // Install it
    val installer = pythonService.getInstaller()
                    ?: return Result.failure(PyCharmCommunityCustomizationBundle.message("misc.project.error.install.not.supported"))
    if (confirmInstallation()) {
      // Install
      when (val r = installer.installLatestPython()) {
        is Result.Failure -> {
          val error = r.error
          logger.warn("Python installation failed $error")
          return Result.Failure(
            PyCharmCommunityCustomizationBundle.message("misc.project.error.install.python", error))
        }
        is Result.Success -> {
          // Find the latest python again, after installation
          systemPythonBinary = pythonService.findSystemPythons().firstOrNull()
        }
      }
    }
  }

  return if (systemPythonBinary == null) {
    Result.Failure(PyCharmCommunityCustomizationBundle.message("misc.project.error.all.pythons.bad"))
  }
  else {
    Result.Success(systemPythonBinary.pythonBinary)
  }
}

private suspend fun openProject(projectPath: Path): Project {
  val projectManager = ProjectManagerEx.getInstanceEx()
  val project = projectManager.openProjectAsync(projectPath, OpenProjectTask {
    runConfigurators = false
  }) ?: error("Failed to open project in $projectPath, check logs")
  // There are countless number of reasons `openProjectAsync` might return null
  if (project.modules.isEmpty()) {
    writeAction {
      ModuleManager.getInstance(project).newModule(projectPath, PythonModuleTypeBase.getInstance().id)
    }
  }
  return project
}

private suspend fun getSdk(pythonPath: PythonBinary, project: Project): Sdk =
  withProgressText(ProjectBundle.message("progress.text.configuring.sdk")) {
    val allJdks = ProjectJdkTable.getInstance().allJdks
    val currentSdk = allJdks.firstOrNull { sdk -> sdk.homeDirectory?.toNioPath() == pythonPath }
    if (currentSdk != null) return@withProgressText currentSdk

    val localPythonVfs = withContext(Dispatchers.IO) { VfsUtil.findFile(pythonPath, true)!! }
    return@withProgressText createSdk(localPythonVfs, project.basePath?.let { Path.of(it) }, allJdks)
  }


/**
 * Creating a project != creating a directory for it, but we need a directory to create a template file
 */
private suspend fun createProjectDir(projectPath: Path): Result<VirtualFile, @Nls String> = withContext(Dispatchers.IO) {
  try {
    projectPath.createDirectories()
  }
  catch (e: IOException) {
    thisLogger().warn("Couldn't create $projectPath", e)
    return@withContext Result.Failure(
      PyCharmCommunityCustomizationBundle.message("misc.project.error.create.dir", projectPath, e.localizedMessage))
  }
  val projectPathVfs = VfsUtil.findFile(projectPath, true)
                       ?: error("Can't find VFS $projectPath")
  return@withContext Result.Success(projectPathVfs)
}

private suspend fun ensureModuleHasRoot(module: Module, root: VirtualFile): Unit = writeAction {
  with(module.rootManager.modifiableModel) {
    try {
      if (root in contentRoots) return@writeAction
      addContentEntry(root)
    }
    finally {
      commit()
    }
  }
}
