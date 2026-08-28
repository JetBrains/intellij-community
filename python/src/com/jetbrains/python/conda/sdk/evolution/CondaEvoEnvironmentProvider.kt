package com.jetbrains.python.conda.sdk.evolution

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
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
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
    // conda itself refuses to recreate an existing named env; that refusal comes back as the returned failure.
    return PyCondaCommand(condaExecutable.path.toString(), null)
      .createCondaSdkAlongWithNewEnv(
        NewCondaEnvRequest.EmptyNamedEnv(languageLevel, envName),
        PythonSdkUtil.getAllSdks(),
        context.pyProject.baseDir,
      )
  }

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
