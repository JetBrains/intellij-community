// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pipenv.sdk.evolution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.pipenv.PipEnvPyTool
import com.intellij.python.pytools.PyTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.toDisplayPath
import com.intellij.python.sdk.backend.evolution.toLeaf
import com.intellij.python.sdk.backend.evolution.toSectionLabel
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.PIPENV_TOOL_ID
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.jetbrains.python.sdk.pipenv.PIP_FILE
import com.jetbrains.python.sdk.pipenv.createPipenvSdk
import com.jetbrains.python.sdk.pipenv.pipfileRequiresPython
import com.jetbrains.python.sdk.pipenv.runPipEnv
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkWithProgressReport
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import com.jetbrains.python.sdk.pipenv.PyPipEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

/**
 * Contributes the "Pipenv" node.
 *
 * Pipenv keeps exactly **one** environment per project, so this node holds one row and no more. That is the difference
 * from poetry, which keeps a cache environment per Python version and therefore offers a row per version. Pipenv also
 * decides *where* that environment goes on its own: an in-project `.venv` when the project already has one, else
 * `$WORKON_HOME` (`~/.local/share/virtualenvs` by default). Neither the location nor the name is a user choice, since
 * pipenv derives the name from a hash of the project path.
 *
 * The environment is therefore normally outside the project, which is why the central discovery never finds it and no
 * other node can show it. Asking pipenv is the only way to know it exists.
 *
 * The node claims no [pyvenvMarker]: pipenv writes only `virtualenv` into `pyvenv.cfg`, which poetry, hatch and the
 * `virtualenv` package write too. Claiming it would attribute another tool's environment to pipenv.
 */
internal class PipenvEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = PipEnvPyTool.getInstance()
  override val toolId: ToolId get() = PIPENV_TOOL_ID

  /** An interpreter of this node's environments carries this flavor, which is what names this node as the active one. */
  override val sdkFlavor: Class<out PythonSdkFlavor<*>> get() = PyPipEnvSdkFlavor::class.java

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val projectDir = pyProject.baseDir
    val envRoot = existingEnvRoot(projectDir, fileSystem)
                  // Nothing to adopt: offer to create the one environment pipenv allows. The section carries no label
                  // because only pipenv knows where the environment will go, and a guessed heading would be wrong as
                  // often as it is right. `addNewEnvSpec` supplies the base-Python choice.
                  ?: return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = emptyList(), addNew = true)))

    // An in-project `.venv` was found by the central discovery, so build its row through [toLeaf] — the same route pip
    // and poetry take. That applies the one marker any tool claims: a `.venv` uv created is drawn with uv's icon and
    // disabled here, so pipenv never wraps a uv-managed environment in a pipenv SDK. It also brings the `pyvenv.cfg`
    // version for free.
    //
    // A cache environment is outside the project and so is absent from [discovered], leaving nothing to classify. It is
    // pipenv's own by construction, since pipenv created it under `$WORKON_HOME`; the frontend resolves its version on
    // hover.
    val leaf = discovered.firstOrNull { it.venvRoot == envRoot }?.toLeaf(this)
               ?: evoEnvLeaf(title = envRoot.name, pythonBinary = envRoot.resolvePythonBinary(), icon = icon)
    val containingFolder = envRoot.parent
    // No "add new" beside it: a second pipenv environment for one project cannot exist.
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(
      label = containingFolder?.toSectionLabel(),
      labelTooltip = containingFolder?.toDisplayPath(),
      leaves = listOf(leaf),
    )))
  }

  /**
   * The environment pipenv manages for [projectDir], or `null` when it has none yet.
   *
   * The `Pipfile` check comes first and is not an optimization. Pipenv searches the parent directories for a `Pipfile`,
   * so in a project without one it can answer with an unrelated environment belonging to a directory above — the user's
   * home included. That is the same trap `setupPipEnv` avoids by writing an empty `Pipfile` before it runs pipenv.
   */
  private suspend fun existingEnvRoot(projectDir: Path, fileSystem: FileSystem<PathHolder.Eel>): Path? {
    if (!projectDir.resolve(PIP_FILE).exists()) return null
    // `pipenv --venv` exits non-zero when the project has no environment, which arrives here as a null.
    val stdout = runPipEnv(fileSystem = fileSystem, dirPath = projectDir, "--venv").getOrNull() ?: return null
    val path = stdout.trim().takeIf { it.isNotBlank() } ?: return null
    // Parsed by the target machine's filesystem rather than the host's, so a remote path keeps its own syntax.
    return fileSystem.parsePath(path).getOrNull()?.path
  }

  /** Adopts the project's existing pipenv environment as a pipenv-typed SDK. */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> =
    createPipenvSdk(context.pyProject.baseDir, PathHolder.Eel(homePath), context.fileSystem)

  /**
   * Creates the project's pipenv environment from the base Python in `token`, then assigns its SDK.
   *
   * `folder` and `name` are unused: pipenv chooses both the location and the name itself — see [addNewEnvSpec].
   * Packages are not installed, matching the other nodes: the user asked for an interpreter, not for a sync.
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val pipenvExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    return setupPipEnvSdkWithProgressReport(
      moduleBasePath = context.pyProject.baseDir,
      basePythonBinaryPath = PathHolder.Eel(Path.of(ref.token)),
      fileSystem = context.fileSystem,
      pipenvExecutable = pipenvExecutable,
      installPackages = false,
    )
  }

  /**
   * The base-Python choice for pipenv's single environment. The name is not editable, and neither the name nor the path
   * reaches pipenv: it names the environment after a hash of the project path, and places it where its own rules say.
   * The project directory name is passed as a readable stand-in only.
   *
   * The versions are narrowed to what the `Pipfile` declares, which is the same rule the other nodes follow against
   * `pyproject.toml`'s `requires-python` — only the file differs, because the `Pipfile` is what pipenv reads. A
   * `[requires] python_version` is a real input: `pipenv install` with no `--python` picks the interpreter from it.
   */
  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    val options = context.systemPythonOptions(pipfileRequiresPython(context.pyProject.baseDir))
                    .takeIf { it.isNotEmpty() } ?: return null
    val baseDir = context.pyProject.baseDir
    return EvoAddNewDto(
      name = baseDir.fileName?.toString() ?: tool.presentableName,
      path = "",
      options = options,
      nameEditable = false,
    )
  }
}
