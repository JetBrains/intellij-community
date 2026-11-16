// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.conda.sdk.configuration

import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.application.readAction
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.intellij.python.pyproject.psi.resolvePythonVersionSpecifiers
import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.sdk.conda.condaSupportedLanguages
import com.jetbrains.python.psi.LanguageLevel
import com.intellij.python.community.execService.python.getLanguageLevelFromVersionStringSafe
import com.intellij.python.sdk.backend.PythonInterpreterRecords
import com.intellij.python.sdk.backend.resolveExecutable
import kotlin.io.path.exists
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.sdk.conda.condaIdentityOf
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlSdkUtils
import com.intellij.python.community.impl.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.PythonEnvironment
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onSuccess
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.getEel
import com.jetbrains.python.project.project
import com.jetbrains.python.project.resolveFile
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnvironment
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.CondaEnvResult
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString


/**
 * This class only supports local, not remote target.
 * TODO: Support remote target (ie \\wsl)
 */
internal class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = CONDA_TOOL_ID

  override val potentialDependencyFiles: Set<String> = CondaEnvironmentYmlSdkUtils.envFileNames

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject) }
    ) { envExists -> { createAndAddSdk(pyProject, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(pyProject: PyProject): EnvCheckerResult =
    withBackgroundProgress(pyProject.project, PyBundle.message("python.sdk.validating.environment")) {
      val condaPath = CondaPyTool.getInstance().resolveExecutable(EelFileSystem(pyProject.getEel()))
                      ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
      val intentionName = PyBundle.message("sdk.create.condaenv.suggestion")

      findExistingEnv(pyProject, condaPath, intentionName)
      ?: if (getEnvironmentYml(pyProject) != null) EnvCheckerResult.EnvNotFound(intentionName)
      else EnvCheckerResult.CannotConfigure
    }

  /**
   * The environment `environment.yml` names, when it already stands on disk, or `null` when it does not.
   *
   * Read from the file system layout, the way the interpreter widget reads a conda environment. The identity says where
   * the environment lives, the interpreter is found in that directory, and [PythonEnvironment.getPythonInfo] answers
   * from the environment's own `conda-meta` entry. Nothing runs conda. This replaced a `conda run ... python` probe
   * that started conda twice on every project open.
   *
   * `null` covers every "not here" case, and the caller turns it into the offer to create the environment: no identity
   * matched, the identity carries no directory, the directory holds no interpreter, or the interpreter is broken.
   */
  private suspend fun findExistingEnv(
    pyProject: PyProject,
    condaExecutable: PathHolder.Eel,
    @IntentionName intentionName: String,
  ): EnvCheckerResult.EnvFound? {
    val envDir = getCondaEnvIdentity(pyProject, condaExecutable)?.envDir ?: return null
    val environment = withContext(Dispatchers.IO) {
      envDir.resolvePythonBinary()?.detectPythonEnvironment()?.getOrNull()
    } ?: return null
    return environment.getPythonInfo().findEnvOrNull(intentionName)
  }

  /**
   * The directory the environment stands in, or `null` when the identity does not carry one.
   *
   * A [PyCondaEnvIdentity.NamedEnv] built from a conda listing carries its path. One restored from a saved SDK does
   * not, and neither does one built from a create request. See [PyCondaEnvIdentity.NamedEnv].
   */
  private val PyCondaEnvIdentity.envDir: Path?
    get() = when (this) {
      is PyCondaEnvIdentity.NamedEnv -> envPath?.toNioPathOrNull()
      is PyCondaEnvIdentity.UnnamedEnv -> envPath.toNioPathOrNull()
    }

  /**
   * The conda environment that the entry names by its identity: its name, or its prefix, see `CondaEnvSdkFlavor`. It is
   * made again at that identity, in the form the entry writes it, so the interpreter keeps the ref of the entry. The
   * `environment.yml` of the project gives its Python and packages. Without the file it is an empty environment, see
   * [pythonLevelOf]. A prefix that is there gives nothing.
   */
  override suspend fun prepareSdkCreatorFor(pyProject: PyProject, key: PythonInterpreterKey): CreateInterpreterInfo? {
    if (key.ref.manager != CondaPyTool.getInstance().fusId) return null
    when (key.ref.mode) {
      PyInterpreterRef.Mode.Native -> Unit
      is PyInterpreterRef.Mode.Target -> return null
    }
    // Remote targets aren't supported yet, as for a new environment.
    if (PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(pyProject.residesOnModule) != null) return null
    val identity = condaIdentityOf(key.ref.env.envRef)
    when (identity) {
      is PyCondaEnvIdentity.NamedEnv -> Unit
      // The prefix can be written relative to the user home, such as `~/app/.conda`. conda reads that form itself.
      is PyCondaEnvIdentity.UnnamedEnv -> if (withContext(Dispatchers.IO) { Path.of(FileUtil.expandUserHome(identity.envPath)).exists() }) return null
    }
    return prepareSdkCreator(
      { EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.recreate.conda.environment", identity.userReadableName)) }
    ) { { recreateCondaEnv(pyProject, key, identity) } }
  }

  /** Makes the conda environment with [identity], which [key] names, and its interpreter. */
  private suspend fun recreateCondaEnv(pyProject: PyProject, key: PythonInterpreterKey, identity: PyCondaEnvIdentity): PyResult<PythonInterpreter> {
    val eel = pyProject.getEel()
    val condaExecutable = EelFileSystem(eel).resolveExecutable(CondaPyTool.getInstance())
    validateCondaPath(condaExecutable?.path?.pathString, eel.getPlatformAndRoot())?.let {
      return PyResult.localizedError(it.message)
    }
    val environmentYml = getEnvironmentYml(pyProject)
    val request = when {
      environmentYml != null -> NewCondaEnvRequest.LocalEnvByLocalEnvironmentFileAt(environmentYml.toNioPath(), identity)
      else -> when (identity) {
        is PyCondaEnvIdentity.NamedEnv -> NewCondaEnvRequest.EmptyNamedEnv(pythonLevelOf(pyProject, key), identity.envName)
        is PyCondaEnvIdentity.UnnamedEnv -> NewCondaEnvRequest.EmptyUnnamedEnv(pythonLevelOf(pyProject, key), identity.envPath)
      }
    }
    return PyCondaCommand(condaExecutable!!.path.pathString, null)
      .createCondaSdkAlongWithNewEnv(ModuleOrProject.ModuleAndProject(pyProject), request, pyProject.baseDir)
  }

  /**
   * The Python of an empty conda environment for [key]: the version that the record of [key] saved, else the newest
   * version that conda supports and the `requires-python` of [pyProject] allows.
   */
  private suspend fun pythonLevelOf(pyProject: PyProject, key: PythonInterpreterKey): LanguageLevel {
    PythonInterpreterRecords.getInstance(pyProject.project).recordOf(key)?.version
      ?.let(::getLanguageLevelFromVersionStringSafe)
      ?.let { return it }
    val specifiers = PyProjectToml.findPyProjectTomlFile(pyProject)?.let { toml ->
      readAction { toml.virtualFile.findPsiFile(pyProject.project)?.resolvePythonVersionSpecifiers() }
    } ?: PyVersionSpecifiers.ANY_SUPPORTED
    return condaSupportedLanguages.firstOrNull { specifiers.isValid(it) } ?: condaSupportedLanguages.first()
  }

  private suspend fun getEnvironmentYml(pyProject: PyProject) = CondaEnvironmentYmlSdkUtils.envFileNames.firstNotNullOfOrNull {
    pyProject.resolveFile(it)?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
  }

  private suspend fun createAndAddSdk(pyProject: PyProject, envExists: Boolean): PyResult<PythonInterpreter> {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(pyProject.residesOnModule)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return PyResult.localizedError(PyBundle.message("sdk.remote.target.are.not.supported.for.conda.environment"))
    }

    val eel = pyProject.getEel()
    val condaExecutable = CondaPyTool.getInstance().resolveExecutable(EelFileSystem(eel))
    validateCondaPath(condaExecutable?.path?.pathString, eel.getPlatformAndRoot())?.let {
      return PyResult.localizedError(it.message)
    }

    val sdk = createAndAddCondaEnv(pyProject, condaExecutable!!, envExists)
    return sdk.onSuccess { interpreter -> PythonInterpreterUpdater.getInstance(pyProject.project).request(interpreter, PythonInterpreterUpdateRequest(packages = true, background = false)) }
  }

  private suspend fun createAndAddCondaEnv(pyProject: PyProject, condaExecutable: PathHolder.Eel, envExists: Boolean): PyResult<PythonInterpreter> {
    thisLogger().debug("Creating conda environment")

    val pythonInterpreter = if (envExists) {
      useExistingCondaEnv(pyProject, condaExecutable)
    }
    else {
      val environmentYml = getEnvironmentYml(pyProject)
                           ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.create.conda.environment.yml.not.found"))
      createCondaEnv(pyProject, condaExecutable, environmentYml).also {
        PySdkConfigurationCollector.logCondaEnv(pyProject.project, CondaEnvResult.CREATED)
      }
    }.getOr { return it }

    // No association step: both branches build the SDK with the pyproject base dir as the working directory, which is
    // what a newly created PythonSdkAdditionalData associates itself with.
    return PyResult.success(pythonInterpreter)
  }

  private suspend fun useExistingCondaEnv(pyProject: PyProject, condaExecutable: PathHolder.Eel): PyResult<PythonInterpreter> {
    val condaIdentity = getCondaEnvIdentity(pyProject, condaExecutable)
                        ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.use.existing.conda.environment"))
    val workingDirectory = pyProject.baseDir
    return PyCondaCommand(condaExecutable.path.pathString, null).createCondaSdkFromExistingEnvironment(
      ModuleOrProject.ModuleAndProject(pyProject),
      condaIdentity,
      workingDirectory,
    )
  }

  private suspend fun getCondaEnvIdentity(pyProject: PyProject, condaExecutable: PathHolder.Eel): PyCondaEnvIdentity? {
    val environmentYml = getEnvironmentYml(pyProject)
    val envName = environmentYml?.let { CondaEnvironmentYmlParser.readNameFromFile(it) }
    val envPrefix = environmentYml?.let { CondaEnvironmentYmlParser.readPrefixFromFile(it) }
    val shouldGuessEnvPrefix = envName == null && envPrefix == null
    val binaryToExec = BinOnEel(condaExecutable.path)
    return PyCondaEnv.getEnvs(binaryToExec).getOr { return null }.firstOrNull {
      val envIdentity = it.envIdentity
      when (envIdentity) {
        is PyCondaEnvIdentity.NamedEnv -> envIdentity.envName == envName
        is PyCondaEnvIdentity.UnnamedEnv -> if (shouldGuessEnvPrefix) {
          val envPath = Path.of(envIdentity.envPath)
          val sameModule = pyProject.baseDir == envPath.parent
          val fileName = Path.of(envIdentity.envPath).name
          !envIdentity.isBase && sameModule && pyProject.resolveFile(fileName) != null
        }
        else envIdentity.envPath == envPrefix
      }
    }?.envIdentity
  }

  private suspend fun createCondaEnv(pyProject: PyProject, condaExecutable: PathHolder.Eel, environmentYml: VirtualFile): PyResult<PythonInterpreter> {
    val project = pyProject.project
    val binaryToExec = BinOnEel(condaExecutable.path)
    val existingEnvs = PyCondaEnv.getEnvs(binaryToExec, forceRefresh = true).getOrNull() ?: emptyList()

    val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(environmentYml.toNioPath(), existingEnvs)
    val workingDirectory = pyProject.baseDir
    val pythonInterpreter = PyCondaCommand(condaExecutable.path.pathString, null)
      .createCondaSdkAlongWithNewEnv(ModuleOrProject.ModuleAndProject(pyProject), newCondaEnvInfo, workingDirectory).getOr {
        PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATION_FAILURE)
        thisLogger().warn("Exception during creating conda environment $it")
        return it
      }

    PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATED)
    return PyResult.success(pythonInterpreter)
  }
}

@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
@ApiStatus.Internal
fun validateCondaPath(
  condaExecutable: @SystemDependent String?,
  platformAndRoot: PlatformAndRoot,
): ValidationInfo? {
  return validateExecutableFile(
    ValidationRequest(
      condaExecutable,
      PyBundle.message("python.add.sdk.conda.executable.path.is.empty"),
      platformAndRoot,
      null
    ))
}
