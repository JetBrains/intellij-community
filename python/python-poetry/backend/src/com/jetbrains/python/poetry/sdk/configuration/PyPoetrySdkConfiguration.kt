// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.sdk.configuration

import com.intellij.util.PathUtil
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.exists
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.sdk.poetry.poetryCacheEnvRefVersion
import com.jetbrains.python.sdk.poetry.POETRY_IN_PROJECT_ENV_REF
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.psi.resolvePythonVersionSpecifiers
import com.intellij.python.community.impl.poetry.backend.PyPoetryBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.poetry.findPoetryLock
import com.jetbrains.python.poetry.getPyProjectTomlForPoetry
import com.intellij.python.community.services.systemPython.getSystemPython
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.eelDescriptor
import com.jetbrains.python.project.getEel
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.CheckToml
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.EnvExists
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.createSdk
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.poetry.runPoetry
import com.jetbrains.python.sdk.poetry.setupPoetry
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.withProject
import com.jetbrains.python.sdk.add.v2.EelOrJustPath
import com.jetbrains.python.sdk.add.v2.EelOrJustPath.Companion.asEelOrJustPath
import com.jetbrains.python.sdk.poetry.POETRY_TOML
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
class PyPoetrySdkConfiguration : PyProjectTomlConfigurationExtension {

  companion object {
    private val LOGGER = Logger.getInstance(PyPoetrySdkConfiguration::class.java)
  }

  override val toolId: ToolId = POETRY_TOOL_ID

  override val potentialDependencyFiles: Set<String> = setOf(PY_PROJECT_TOML, POETRY_TOML)

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject, true) },
    ) { envExists -> { createPoetry(pyProject, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject, false) },
    ) { envExists -> { createPoetry(pyProject, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  /**
   * The poetry environment that the entry names: the in-project `.venv`, or the cache environment of one Python version,
   * see [poetryEnvRefOf]. An env ref of another form names an environment by its path, and poetry does not place one
   * there, so it gives nothing. So does an in-project `.venv` that is there.
   */
  override suspend fun prepareSdkCreatorFor(pyProject: PyProject, key: PythonInterpreterKey): CreateInterpreterInfo? {
    if (key.ref.manager != PoetryPyTool.getInstance().fusId) return null
    when (key.ref.mode) {
      PyInterpreterRef.Mode.Native -> Unit
      is PyInterpreterRef.Mode.Target -> return null
    }
    val envRef = key.ref.env.envRef.value
    val target = when {
      envRef == POETRY_IN_PROJECT_ENV_REF -> PoetryEnvTarget(inProject = true, pythonVersion = null)
      CACHE_ENV_REF.matches(envRef) -> PoetryEnvTarget(inProject = false, pythonVersion = poetryCacheEnvRefVersion(envRef))
      else -> return null
    }
    if (target.inProject && withContext(Dispatchers.IO) { pyProject.baseDir.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME).exists() }) {
      return null
    }
    val intentionName = when (val version = target.pythonVersion) {
      null -> PyPoetryBundle.message("sdk.recreate.poetry.in.project.environment")
      else -> PyPoetryBundle.message("sdk.recreate.poetry.cache.environment", version)
    }
    return prepareSdkCreator({ EnvCheckerResult.EnvNotFound(intentionName) }) { { createPoetry(pyProject, envExists = false, target) } }
  }

  private suspend fun checkManageableEnv(
    pyProject: PyProject, checkToml: CheckToml,
  ): EnvCheckerResult = reportRawProgress {
    it.text(PySdkBundle.message("python.sdk.validating.environment"))
    val poetryLockExists = findPoetryLock(pyProject) != null
    val pyProjectToml = withContext(Dispatchers.IO) { PyProjectToml.findPyProjectTomlFile(pyProject) }

    val isPoetryProject = if (checkToml) {
      withContext(Dispatchers.IO) {
        pyProjectToml?.let { toml -> getPyProjectTomlForPoetry(toml.virtualFile) } != null || poetryLockExists
      }
    }
    else true

    val canManage = isPoetryProject && pyProject.getEel().toFileSystem().resolveExecutable(PoetryPyTool.getInstance()) != null
    val intentionName = PyPoetryBundle.message("sdk.set.up.poetry.environment")
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    if (canManage) {
      if (pyProjectToml != null && !poetryLockExists) return@reportRawProgress envNotFound
      val basePath = pyProject.baseDir.asEelOrJustPath()
      runPoetry(basePath, "check", "--lock").getOr { return@reportRawProgress envNotFound }
      val envPath = findPoetryEnvHome(basePath).getOr { return@reportRawProgress envNotFound }
      envPath?.resolvePythonBinary()?.findEnvOrNull(intentionName) ?: envNotFound
    }
    /**
     * We're confident that it's a poetry project in two cases:
     * - File poetry.lock exists
     * - We checked pyproject.toml and there's a specific mention of poetry tool
     */
    else if (poetryLockExists || (isPoetryProject && checkToml)) {
      // poetry was just installed; drop the detection cache so the next lookup finds it (don't persist).
      val pathPersister: (Path) -> Unit = { _ ->
        PyExecutableCache.getInstance().invalidate(pyProject.eelDescriptor, PoetryPyTool.getInstance())
      }
      val tool = PoetryPyTool.getInstance()
      EnvCheckerResult.SuggestToolInstallation(
        toolToInstall = tool.packageName.name,
        pathPersister = pathPersister,
        intentionName = PyPoetryBundle.message("sdk.create.custom.venv.install.fix.title", "Poetry")
      )
    }
    else EnvCheckerResult.CannotConfigure
  }

  private suspend fun createPoetry(pyProject: PyProject, envExists: EnvExists, target: PoetryEnvTarget? = null): PyResult<PythonInterpreter> =
    withBackgroundProgress(pyProject.project, PyPoetryBundle.message("sdk.progress.text.setting.up.poetry.environment")) {
      val basePath = pyProject.baseDir
      val existingEnv =
        if (envExists) {
          findPoetryEnvHome(basePath.asEelOrJustPath()).getOr { return@withBackgroundProgress it }
        }
        else {
          null
        }

      if (envExists && existingEnv == null) {
        LOGGER.warn(
          "Can't find existing poetry environment in project, but it was expected.wProbably it was deleted. New environment will be created"
        )
      }

      val poetry = existingEnv ?: createPoetryEnv(pyProject, target).getOr { return@withBackgroundProgress it }
      val path =
        poetry.resolvePythonBinary() ?: return@withBackgroundProgress PyResult.localizedError(
          PySdkBundle.message("cannot.find.executable", "python", poetry)
        )
      val file =
        StandardFileSystems.local().refreshAndFindFileByPath(path.pathString) ?: return@withBackgroundProgress PyResult.localizedError(
          PySdkBundle.message("cannot.find.executable", "python", path)
        )

      LOGGER.debug("Setting up associated poetry environment: $path, $basePath")

      val sdk = createSdk(
        pyProject,
        PathHolder.Eel(file.toNioPath()),
        PyPoetrySdkAdditionalData(basePath),
        suggestedSdkName(basePath)
      ).getOr { return@withBackgroundProgress it }

      PyResult.success(sdk)
    }

  /**
   * Creates the poetry environment of [pyProject]. [target] places it, see [PoetryEnvTarget]. Without it, the base Python
   * follows `requires-python`, and the poetry setting `virtualenvs.in-project` decides the place.
   */
  private suspend fun createPoetryEnv(pyProject: PyProject, target: PoetryEnvTarget? = null): PyResult<Path> {
    LOGGER.debug("Creating poetry environment")

    val basePath = pyProject.baseDir
    val tomlFile = PyProjectToml.findPyProjectTomlFile(pyProject)
    val versionSpecifiers = target?.pythonVersion?.let { PyVersionSpecifiers("==$it") } ?: tomlFile?.let { pyProjectTomlFile ->
      readAction { pyProjectTomlFile.virtualFile.findPsiFile(pyProject.project)?.resolvePythonVersionSpecifiers() }
    } ?: PyVersionSpecifiers.ANY_SUPPORTED

    val baseSystemPython = getSystemPython(
      confirmInstallation = { true },
      pythonService = SystemPythonService(),
      versionSpecifiers = versionSpecifiers,
      eelDescriptor = pyProject.eelDescriptor,
    ).getOr { return it }

    val inProjectEnv = target?.inProject ?: (runPoetry(basePath.asEelOrJustPath(), "config", "virtualenvs.in-project")
      .getOr { return it }
      .trim() == "true")

    return setupPoetry(
      projectPath = basePath,
      basePythonBinaryPath = baseSystemPython.pythonBinary,
      installPackages = true,
      init = tomlFile == null,
      errorSink = ErrorSink().withProject(pyProject.project),
      inProjectEnv = inProjectEnv,
    )
  }

  private suspend fun findPoetryEnvHome(basePath: EelOrJustPath): PyResult<Path?> =
    runPoetry(basePath, "env", "info", "-p").mapSuccess { it.trim().toNioPathOrNull() }
}

/**
 * Where a poetry environment goes: the in-project `.venv` when [inProject], else the cache environment of Python
 * [pythonVersion], such as `3.13`. `null` [pythonVersion] lets `requires-python` choose the base Python.
 */
private data class PoetryEnvTarget(val inProject: Boolean, val pythonVersion: String?)

/** The env ref of a poetry cache environment, see [poetryCacheEnvRef]. */
private val CACHE_ENV_REF = Regex("""py\d+\.\d+""")

private fun suggestedSdkName(basePath: Path): @NlsSafe String = "Poetry (${PathUtil.getFileName(basePath.pathString)})"

