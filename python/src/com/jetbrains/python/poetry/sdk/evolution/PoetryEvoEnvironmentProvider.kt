package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.pytools.PyTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
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
import com.jetbrains.python.sdk.evolution.systemPythonOptions
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.poetry.createNewPoetrySdk
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.poetry.runPoetry
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val VERSIONS_KEY: String = "poetry.systemPythons"

internal class PoetryEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = PoetryPyTool.getInstance()
  override val toolId: ToolId get() = POETRY_TOOL_ID

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val projectDir = pyProject.baseDir
    // Poetry's cache environments, as full env-root paths. Force `virtualenvs.in-project=false` (as the v2 dialog does)
    // so poetry enumerates the cache envs even when an in-project `.venv` exists — otherwise it reports only `.venv`.
    val poetryEnvRoots: List<Path> = runPoetry(projectDir, "env", "list", "--full-path", inProjectEnv = false).getOrNull()
      ?.lineSequence()
      ?.map { Path.of(it.removeSuffix("(Activated)").trim()) }
      ?.filter { it.name.isNotBlank() }
      ?.toList()
      ?: emptyList()

    // (a) In-project: exactly the project's `.venv` (poetry's only in-project location — it can't be `.venv1` nor more
    // than one). Show it if it exists, even if poetry didn't create it, and then hide "add new"; otherwise offer an
    // "add new" that creates the in-project env with a chosen Python version (PyEvoSdkApiProvider: inProjectEnv).
    val inProjectVenv = discovered.firstOrNull { it.venvRoot == defaultVenvDir(projectDir) }
    val inProjectSection = EvoSectionDto(
      label = PySdkBundle.message("evolution.poetry.in.project"),
      leaves = listOfNotNull(inProjectVenv?.toLeaf(icon)),
      addNew = inProjectVenv == null,
      addNewFolderPath = projectDir.pathString,
    )

    // (b) Poetry cache: one row per system-python major version — an existing cache env → select it (points straight at
    // that env's python); otherwise create a poetry cache env from that system Python ([evoCreateEnvLeaf] carries the
    // base python as the token, no folder → inProjectEnv=false). Shown regardless of an in-project `.venv`.
    // One row per allowed system Python — the same list the add-new picker offers, so the two never disagree. Each
    // option's title is the bare version and its token the interpreter path, which is what poetry creates the env from.
    val perVersionLeaves = systemPythonOptions(projectDir, fileSystem).map { option ->
      val versionStr = option.title
      // These rows are identified by the Python they hold rather than by an env name, so spell that out the way the
      // add-new version rows do ("Python 3.13") instead of showing a bare number. Only the label changes: the lookup
      // below still matches on the plain version, which is what poetry puts at the end of the cache env's folder name.
      val title = PySdkBundle.message("evolution.python.version", versionStr)
      val existingBinary = poetryEnvRoots.firstOrNull { it.name.endsWith(versionStr) }?.resolvePythonExecutable()
      if (existingBinary != null) evoEnvLeaf(title = title, pythonBinary = existingBinary, icon = icon)
      else evoCreateEnvLeaf(title = title, token = option.token, icon = icon, bases = option.bases)
    }
    val virtualenvsDir = runPoetry(projectDir, "config", "virtualenvs.path").getOrNull()?.trim()?.takeIf { it.isNotBlank() }
      ?.let { Path.of(it) }
    val cacheSection = if (perVersionLeaves.isEmpty()) null
    else EvoSectionDto(
      label = virtualenvsDir?.toSectionLabel(),
      labelTooltip = virtualenvsDir?.toDisplayPath(),
      leaves = perVersionLeaves,
    )

    return EvoLoadResultDto.Ok(listOf(inProjectSection) + listOfNotNull(cacheSection))
  }

  /** Adopts an existing poetry env (in-project `.venv` or a cache env) as a poetry-typed SDK. */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> =
    createPoetrySdk(context.pyProject.baseDir, PathHolder.Eel(homePath), context.fileSystem)

  /**
   * Creates a poetry env from the base Python in `token`.
   *
   * `folder` being set is what distinguishes the two kinds of row: the in-project section's add-new carries the target
   * directory and wants poetry's `virtualenvs.in-project`, while a per-version cache row carries none and lets poetry
   * place the env in its own cache.
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val poetryExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    return createNewPoetrySdk(
      moduleBasePath = context.pyProject.baseDir,
      basePythonBinaryPath = PathHolder.Eel(Path.of(ref.token)),
      fileSystem = context.fileSystem,
      poetryExecutable = poetryExecutable,
      installPackages = false,
      errorSink = context.errorSink,
      inProjectEnv = ref.folder != null,
      targetPanelExtension = null,
    )
  }

  /**
   * Poetry's in-project env is always `.venv` — poetry ignores any other name — so the name is shown but not editable,
   * unlike uv's and pip's freely-named folders.
   */
  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    val baseDir = context.pyProject.baseDir
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(baseDir, context.fileSystem) }
      .takeIf { it.isNotEmpty() } ?: return null
    val dir = defaultVenvDir(section.addNewFolderPath?.let { Path.of(it) } ?: baseDir)
    return EvoAddNewDto(name = dir.fileName.toString(), path = dir.pathString, options = options, nameEditable = false)
  }
}
