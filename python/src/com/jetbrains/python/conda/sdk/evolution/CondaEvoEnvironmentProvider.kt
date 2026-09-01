package com.jetbrains.python.conda.sdk.evolution

import kotlin.io.path.pathString
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.runTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.toDisplayPath
import com.intellij.python.sdk.backend.evolution.toSectionLabel
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.conda.condaSupportedLanguages
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.resolvePythonBinary
import java.nio.file.Path
import kotlin.io.path.name
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

/** Fallback env-name stem when the project directory has no usable name. */
private const val DEFAULT_ENV_NAME: String = "conda"

internal class CondaEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = CondaPyTool.getInstance()
  override val toolId: ToolId get() = CONDA_TOOL_ID

  /** An interpreter of this node's environments carries this flavor, which is what names this node as the active one. */
  override val sdkFlavor: Class<out PythonSdkFlavor<*>> get() = CondaEnvSdkFlavor::class.java

  override val stepDescription: String get() = PySdkBundle.message("evolution.node.step.conda")

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    // Presence check only: the rows are grouped by where each env lives, not by where conda itself is installed.
    executableOrNull(fileSystem) ?: return evoWarning(PyCondaBundle.message("evolution.conda.executable.is.not.found"))
    val stdout = tool.runTool(fileSystem, null, null, "env", "list").getOrNull()
                 ?: return EvoLoadResultDto.Ok(emptyList())
    val envs = parseEnvList(stdout)
    // One section per folder the envs actually live in, labelled with that folder — the same grouping the venv-based tools
    // use. Conda keeps a base env at the installation root and the named ones under its `envs/`, so this separates the two
    // (and keeps several conda installations apart) instead of filing everything under one heading.
    val envSections = envs.groupBy { it.root.parent }.map { (containingFolder, group) ->
      EvoSectionDto(
        label = containingFolder?.toSectionLabel(),
        labelTooltip = containingFolder?.toDisplayPath(),
        leaves = group.map { evoEnvLeaf(it.name, it.binary, icon) },
      )
    }
    // Conda envs are named (not folder-based): propose a free env name derived from the project so the widget's
    // in-place "add new" can offer name + Python version (PyEvoSdkApiProvider fills the version options), instead of
    // the modal dialog. addNewFolderPath carries the proposed name here.
    // Named after the project, and offered only while nothing carries that name: the row stands for the environment
    // this project would get, so where that one already exists it is in the list above and there is nothing to add.
    val proposedName = pyProject.baseDir.fileName?.toString() ?: DEFAULT_ENV_NAME
    val addNewSection = proposedName
      .takeIf { name -> envs.none { it.name == name } }
      ?.let {
        EvoSectionDto(
          // Under a heading of its own, so a row that creates something is never read as one of the environments listed.
          label = PySdkBundle.message("evolution.section.new.environment"),
          leaves = emptyList(),
          addNew = true,
          addNewFolderPath = it,
        )
      }
    // First, not last: conda lists every environment on the machine, not only the project's, so the list is long and
    // grows, and a row placed after it would sit further from the pointer the more environments the user has. The other
    // tools list a handful of environments from the project itself, where such a row keeps its place under them.
    return EvoLoadResultDto.Ok(listOfNotNull(addNewSection) + envSections)
  }

  /** Adopts an existing conda env (named or `-p`-created) as a conda-typed SDK, matched by the env directory. */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> {
    val condaExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val envDir = homePath.parent?.parent
                 ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", homePath.toString()))
    val envs = PyCondaEnv.getEnvs(PyCondaCommand(condaExecutable.path.toString(), null).asBinaryToExec()).getOr { return it }
    val env = envs.firstOrNull { candidate ->
      when (val identity = candidate.envIdentity) {
        // conda reports an unnamed env by path; a path it cannot parse simply is not this env.
        is PyCondaEnvIdentity.UnnamedEnv -> identity.envPath.toNioPathOrNull() == envDir
        is PyCondaEnvIdentity.NamedEnv -> envDir.fileName?.toString() == identity.envName
      }
    } ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", envDir.toString()))
    return env.createSdkFromThisEnv(null, PythonSdkUtil.getAllSdks(), context.pyProject.baseDir)
  }

  /**
   * Creates a named conda env: `name` is the (possibly user-edited) env name and `token` the chosen Python version.
   *
   * No base interpreter is involved — conda provides the Python for the requested version itself, which is why its
   * version list is conda's own rather than the machine's system Pythons.
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val condaExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val envName = ref.name?.takeIf { it.isNotBlank() } ?: ref.folder?.takeIf { it.isNotBlank() }
                  ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.name.missing"))
    val languageLevel = LanguageLevel.fromPythonVersion(ref.token)
                        ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.bad.python.version", ref.token, ""))
    return PyCondaCommand(condaExecutable.path.toString(), null)
      .createCondaSdkAlongWithNewEnv(
        NewCondaEnvRequest.EmptyNamedEnv(languageLevel, envName),
        PythonSdkUtil.getAllSdks(),
        context.pyProject.baseDir,
      )
  }

  /**
   * A conda env may be rebuilt on any Python conda itself can install, which is the same list "add new" offers.
   *
   * No base Python is chosen from the machine: conda fetches the interpreter for the version asked for, so these rows
   * are conda's own supported levels rather than what is installed here.
   *
   * A conda *installation's* base environment is refused — see [namedEnvDir]. Nothing is offered to fill the rebuilt
   * environment with: conda keeps no lock file, and an `environment.yml` is a separate flow from this one.
   */
  override suspend fun recreateSpecFor(context: EvoToolContext, leaf: EvoLeafDto): EvoRecreateDto? {
    val binary = (leaf.ref as? PyInterpreterRef.DetectedPath)?.homePath?.toNioPathOrNull() ?: return null
    namedEnvDir(binary) ?: return null
    val options = condaSupportedLanguages.map { EvoAddNewOptionDto(title = it.toPythonVersion(), token = it.toPythonVersion()) }
    return options.takeIf { it.isNotEmpty() }?.let { EvoRecreateDto(options = it) }
  }

  /**
   * Rebuilds the env in place: `conda create -p <env dir> python=<version>`, which replaces what stands there.
   *
   * conda does the destroying itself. Asked to create over an environment that exists it offers to remove that one
   * first, and answers its own question with `-y` — so this is one step rather than a delete followed by a create.
   *
   * By path and not by name, though these environments have names: a name is resolved against whichever conda
   * installation runs the command, and the widget lists the environments of every installation on the machine. The path
   * names exactly the environment the row stands for.
   *
   * The SDK is then built by [createSdkForExistingEnv], the same call that adopts the row when it is simply selected,
   * so a rebuilt environment is typed exactly as the one it replaced.
   *
   * Windows is the known weak spot: conda cannot always replace an environment in place there, and says so — see
   * [NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile], which updates rather than recreates for that reason.
   */
  override suspend fun recreateEnv(context: EvoToolContext, homePath: Path, spec: EvoRecreateSpec): PyResult<Sdk> {
    val condaExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val envDir = namedEnvDir(homePath)
                 ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.conda.base.env", homePath.toString()))
    val languageLevel = LanguageLevel.fromPythonVersion(spec.baseToken)
                        ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.bad.python.version", spec.baseToken, ""))
    PyCondaEnv.createEnv(
      PyCondaCommand(condaExecutable.path.toString(), null),
      NewCondaEnvRequest.EmptyUnnamedEnv(languageLevel, envDir.pathString),
    ).getOr { return it }
    return createSdkForExistingEnv(context, homePath)
  }

  /**
   * The directory of the environment whose interpreter is [binary], or null when that is an installation's base env.
   *
   * conda keeps its named environments under `envs/` and the base one at the installation root, so the parent folder is
   * what tells them apart. The distinction has to be made: rebuilding an environment removes everything under its
   * directory, and for a base environment that directory holds the whole conda installation, `envs/` included. conda
   * spells this out itself when asked — "This will remove ALL directories contained within this specified prefix
   * directory, including any other conda environments."
   */
  private fun namedEnvDir(binary: Path): Path? =
    binary.parent?.parent?.takeIf { it.parent?.name == "envs" }

  /**
   * Conda envs are named rather than folder-based, so the editable field is the env *name* and `path` is unused.
   *
   * The proposed name comes from the section [loadSections] built, which already picked one that is free; the versions
   * are conda's own supported levels, deliberately *not* filtered by the project's `requires-python` — matching the v2
   * dialog, since conda can install any of them rather than having to find one on the machine.
   */
  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    val options = condaSupportedLanguages.map { EvoAddNewOptionDto(title = it.toPythonVersion(), token = it.toPythonVersion()) }
    if (options.isEmpty()) return null
    val envName = section.addNewFolderPath ?: context.pyProject.baseDir.fileName?.toString() ?: DEFAULT_ENV_NAME
    // The name is proposed and shown, never typed: every tool now names its own environment, so the widget offers one
    // choice per step — which Python — instead of a form.
    return EvoAddNewDto(name = envName, path = "", options = options)
  }

  /**
   * One environment from `conda env list`: its [name], the [root] directory it lives in (which is what the rows are grouped
   * by), and its interpreter — null when the env has no runnable python, which renders as a display-only "n/a" row.
   */
  private data class CondaEnv(val name: @NlsSafe String, val root: Path, val binary: Path?)

  private fun parseEnvList(stdout: String): List<CondaEnv> =
    stdout.trim().lines()
      .filter { !it.startsWith('#') }
      .mapNotNull { line ->
        val parts = line.split("\\s+".toRegex())
        val pathStr = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val root = Path.of(pathStr)
        // An env created with `-p` has no name column, so the line starts with the marker/path: fall back to the dir name.
        val realName = parts.first().takeIf { it.isNotBlank() } ?: root.name
        CondaEnv(realName, root, root.resolvePythonBinary())
      }
}
