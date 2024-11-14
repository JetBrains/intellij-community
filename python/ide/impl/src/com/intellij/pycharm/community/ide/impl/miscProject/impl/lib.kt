// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.execution.ExecutionException
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
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
import com.intellij.pycharm.community.ide.impl.miscProject.impl.ObtainPythonStrategy.FindOnSystem
import com.intellij.pycharm.community.ide.impl.miscProject.impl.ObtainPythonStrategy.UseThesePythons
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.LocalizedErrorString
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.Result
import com.jetbrains.python.mapResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkToInstallManager
import com.jetbrains.python.sdk.PythonBinary
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.sdk.add.v2.createSdk
import com.jetbrains.python.sdk.add.v2.createVirtualenv
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.installer.installBinary
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.pathString

private val logger = fileLogger()

internal val miscProjectDefaultPath: Lazy<Path> = lazy { Path.of(SystemProperties.getUserHome()).resolve("PyCharmMiscProject") }
internal val miscProjectEnabled: Boolean get() = Registry.`is`("pycharm.miscProject")

/**
 * Creates project in [projectPath] in modal window. Once created, uses [scopeProvider] to get scope
 * to launch [miscFileType] generation in background, returns it as a job.
 *
 * Pythons are obtained with [obtainPythonStrategy]
 */
@RequiresEdt
fun createMiscProject(
  miscFileType: MiscFileType,
  scopeProvider: (Project) -> CoroutineScope,
  obtainPythonStrategy: ObtainPythonStrategy,
  projectPath: Path = miscProjectDefaultPath.value,
): Result<Job, LocalizedErrorString> =
  runWithModalProgressBlocking(ModalTaskOwner.guess(),
                               PyCharmCommunityCustomizationBundle.message("misc.project.generating.env"),
                               TaskCancellation.cancellable()) {
    createProjectAndSdk(projectPath, obtainPythonStrategy)
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
  return withContext(Dispatchers.EDT) {
    val psiFile = readAction { PsiManager.getInstance(project).findFile(vfsFile) } ?: error("Can't find PSI for $vfsFile")
    psiFile.navigate(true)
    psiFile
  }
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
 * Creates project with 1 module in [projectPath] and sdk using the highest python.
 * Pythons are searched in system ([findPythonsOnSystem]) or provided explicitly (depends on [obtainPythonStrategy]).
 * In former case if no python were found, we [installLatestPython] (not in a latter case, though).
 */
private suspend fun createProjectAndSdk(
  projectPath: Path,
  obtainPythonStrategy: ObtainPythonStrategy,
): Result<Pair<Project, Sdk>, LocalizedErrorString> {
  val projectPathVfs = createProjectDir(projectPath).getOr { return it }
  val venvDirPath = projectPath.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)

  // Find venv in project
  var venvPython: PythonBinary? = findExistingVenv(venvDirPath)

  if (venvPython == null) {
    // No venv found -- find system python to create venv
    val systemPythonBinary = getSystemPython(obtainPythonStrategy).getOr { return it }
    logger.info("no venv in $venvDirPath, using system python $systemPythonBinary to create venv")
    // create venv using this system python
    createVenv(systemPythonBinary, venvDirPath = venvDirPath, projectPath = projectPath).getOr { return it }
    // try to find venv again
    venvPython = findExistingVenv(venvDirPath)
    if (venvPython == null) {
      // No venv even after venv installation
      return Result.failure(LocalizedErrorString(PyCharmCommunityCustomizationBundle.message("misc.project.error.create.venv", "", venvDirPath)))
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
  if (flavor.getVersionString(pythonPath.toString()) == null) {
    logger.warn("No version string. python seems to be broken: $pythonPath")
    return@withContext null
  }
  return@withContext pythonPath
}

private suspend fun createVenv(systemPython: PythonBinary, venvDirPath: Path, projectPath: Path): Result<Unit, LocalizedErrorString> =
  try {
    createVirtualenv(systemPython, venvDirPath, projectPath)
    Result.success(Unit)
  }
  catch (e: ExecutionException) {
    Result.failure(LocalizedErrorString(PyCharmCommunityCustomizationBundle.message("misc.project.error.create.venv", e.toString(), venvDirPath)))
  }

private suspend fun getSystemPython(obtainPythonStrategy: ObtainPythonStrategy): Result<PythonBinary, LocalizedErrorString> {
  // First, find the latest python according to strategy
  var systemPythonBinary = filterLatestPython(
    when (obtainPythonStrategy) {
      is UseThesePythons -> obtainPythonStrategy.pythons
      is FindOnSystem -> findPythonsOnSystem()
    })

  // No python found?
  if (systemPythonBinary == null) {
    // Only install if pythons weren't provided explicitly, see fun doc
    when (obtainPythonStrategy) {
      is UseThesePythons -> Unit
      is FindOnSystem -> {
        // User is ok with installation
        if (obtainPythonStrategy.confirmInstallation()) {
          // Install
          installLatestPython().onFailure { exception ->
            // Failed to install python?
            logger.warn("Python installation failed", exception)
            return Result.Failure(LocalizedErrorString(
              PyCharmCommunityCustomizationBundle.message("misc.project.error.install.python", exception.toString())))
          }
        }
      }
    }
    // Find latest python again, after installation
    systemPythonBinary = filterLatestPython(findPythonsOnSystem())
    logger.warn("No python found even after installation")
  }

  return if (systemPythonBinary == null) {
    Result.Failure(LocalizedErrorString(PyCharmCommunityCustomizationBundle.message("misc.project.error.all.pythons.bad")))
  }
  else {
    Result.Success(systemPythonBinary)
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
 * Creating project != creating directory for it, but we need directory to create template file
 */
private suspend fun createProjectDir(projectPath: Path): Result<VirtualFile, LocalizedErrorString> = withContext(Dispatchers.IO) {
  try {
    projectPath.createDirectories()
  }
  catch (e: IOException) {
    thisLogger().warn("Couldn't create $projectPath", e)
    return@withContext Result.Failure(LocalizedErrorString(
      PyCharmCommunityCustomizationBundle.message("misc.project.error.create.dir", projectPath, e.localizedMessage)))
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

/**
 * Looks for system pythons. Returns flavor and all its pythons.
 */
fun findPythonsOnSystem(): List<Pair<PythonSdkFlavor<*>, Collection<Path>>> =
  PythonSdkFlavor.getApplicableFlavors(true)
    .map { flavor ->
      flavor.dropCaches()
      flavor to flavor.suggestLocalHomePaths(null, null)
    }
    .filter { (_, pythons) ->
      pythons.isNotEmpty() // No need to have flavors without pythons
    }

suspend fun installLatestPython(): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
  val pythonToInstall = PySdkToInstallManager.getAvailableVersionsToInstall().toSortedMap().values.last()
  return@withContext withContext(Dispatchers.EDT) {
    installBinary(pythonToInstall, null) {
    }
  }
}

/**
 * Looks for the latest python among [flavorsToPythons]: each flavour might have 1 or more pythons.
 * Broken pythons are filtered out. If `null` is returned, no python found, you probably need to [installLatestPython]
 */
private suspend fun filterLatestPython(flavorsToPythons: List<Pair<PythonSdkFlavor<*>, Collection<Path>>>): PythonBinary? {
  var current: Pair<LanguageLevel, Path>? = null
  for ((flavor, paths) in flavorsToPythons) {
    for (pythonPath in paths) {
      val versionString = withContext(Dispatchers.IO) { flavor.getVersionString(pythonPath.pathString) } ?: continue
      val languageLevel = flavor.getLanguageLevelFromVersionString(versionString)

      // Highest possible, no need to search further
      if (languageLevel == LanguageLevel.getLatest()) {
        return pythonPath
      }
      if (current == null || current.first < languageLevel) {
        // More recent Python found!
        current = Pair(languageLevel, pythonPath)
      }
    }
  }

  return current?.second
}


