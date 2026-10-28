package com.jetbrains.python.poetry.sdk.evolution

import com.jetbrains.python.getOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.intellij.python.sdk.backend.evolution.envNotFound
import com.jetbrains.python.sdk.add.v2.FileSystemWithEel
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.poetry.poetryCacheEnvFor
import com.jetbrains.python.sdk.poetry.poetryCacheEnvRoots
import com.jetbrains.python.sdk.poetry.poetryEnvRootOf
import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.sdk.poetry.poetryCacheEnvRef
import com.jetbrains.python.sdk.poetry.poetryCacheEnvRefVersion
import com.jetbrains.python.sdk.poetry.poetryEnvRefOf
import com.jetbrains.python.sdk.poetry.POETRY_IN_PROJECT_ENV_REF
import com.jetbrains.python.project.PyProject
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.evolution.toLeaf
import com.intellij.python.sdk.backend.evolution.ownedEnvDirOf
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoInstallPythonLeaf
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.intellij.python.sdk.common.EvoRowAction
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.EelOrJustPath.Companion.asEelOrJustPath
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.evolution.deleteEnvDir
import com.jetbrains.python.sdk.evolution.systemPythonOptions
import com.jetbrains.python.sdk.poetry.createNewPoetrySdk
import com.jetbrains.python.sdk.poetry.runPoetry
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val VERSIONS_KEY: String = "poetry.systemPythons"

/** [EvoToolContext.cached] key for the cache environments `poetry env list` reports. */
private const val ENVS_KEY: String = "poetry.cacheEnvs"

internal class PoetryEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val tool: PyTool get() = PoetryPyTool.getInstance()
  override val label: String get() = PySdkBundle.message("evolution.node.label.poetry")
  override val icon get() = PythonCommunityImplPoetryCommonIcons.Poetry
  override val toolId: ToolId get() = POETRY_TOOL_ID

  override val stepDescription: String get() = PySdkBundle.message("evolution.node.step.poetry")

  /**
   * The project's own `.venv`, and nothing that costs a Python scan.
   *
   * The cache rows are built in [decorate] instead, which is handed the context and so can share its memoized interpreter
   * list. Built here they went through the uncached entry point and made the node scan the machine twice — once for
   * these rows and once for the choices every row now carries.
   */
  override suspend fun loadSections(context: EvoToolContext, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val projectDir = context.workspace.baseDir
    // Exactly the project's `.venv` — poetry's only in-project location, it can't be `.venv1` nor more than one. Shown
    // if it exists, even when poetry did not create it, and then no "add new"; otherwise the row that creates it.
    val inProjectVenv = discovered.firstOrNull { it.venvRoot == defaultVenvDir(projectDir) }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(
      label = PySdkBundle.message("evolution.poetry.in.project"),
      // Named [POETRY_IN_PROJECT_ENV_REF] by [interpreterRefOf], since it is the project's `.venv`.
      leaves = listOfNotNull(inProjectVenv?.toLeaf(this, context.workspace.pyProject)),
      addNew = inProjectVenv == null,
      addNewFolderPath = projectDir.pathString,
    )))
  }

  /**
   * Appends the cache rows: one per Python version poetry could use, each already carrying the interpreters behind it.
   *
   * Here rather than in [loadSections] so the interpreter list is the one memoized for this request — the same list
   * [addNewEnvSpec] and [recreateSpecFor] read, computed once for the whole node load instead of once per caller.
   */
  override suspend fun decorate(context: EvoToolContext, result: EvoLoadResultDto): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    val projectDir = context.workspace.baseDir
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(projectDir, context.fileSystem) }
    if (options.isEmpty()) return result
    val poetryEnvRoots = cacheEnvRoots(context)

    val perVersionLeaves = options.map { option ->
      val versionStr = option.title
      // These rows are identified by the Python they hold rather than by an env name, so spell that out the way the
      // add-new version rows do ("Python 3.13") instead of showing a bare number. Only the label changes: the lookup
      // below still matches on the plain version, which is what poetry puts at the end of the cache env's folder name.
      val title = PySdkBundle.message("evolution.python.version", versionStr)
      val existingBinary = poetryEnvRoots.map(PathHolder::Eel).poetryCacheEnvFor(versionStr)?.path?.resolvePythonBinary()
      val leaf = when {
        // Not on the machine: offer to install it. Its token is the version rather than an interpreter path, so it
        // cannot be handed to the create step as-is — evoInstallPythonLeaf is what asks for the install first.
        option.installable -> evoInstallPythonLeaf(title = title, version = versionStr)
        existingBinary != null -> evoEnvLeaf(title = title, pythonBinary = existingBinary, envRef = PyEnvRef(poetryCacheEnvRef(versionStr)))
        // Built from the best interpreter of that version, with the others behind the row's own pencil rather than
        // behind a view of the whole node — see [recreateSpecFor].
        else -> evoCreateEnvLeaf(title = title, token = option.token, icon = icon)
          // The build this row would actually use, which is the one [EvoAddNewOptionDto.token] names — the newest that
          // is installed. Showing the first of the list instead named the newest uv *knows*, so a row that was about to
          // build on 3.14.5 from this machine announced the 3.14.6 uv would have had to download.
          .copy(createVersions = listOf(option), secondaryText = option.defaultBaseVersion())
      }
      leaf.copy(versionGroup = title)
    }
    // Headed by where poetry keeps these rather than by the directory it keeps them in. The directory would cost a
    // `poetry config virtualenvs.path` run of its own — a whole poetry start-up for a heading that says no more.
    val cacheSection = EvoSectionDto(label = PySdkBundle.message("evolution.poetry.in.caches"), leaves = perVersionLeaves)
    return result.copy(sections = result.sections + cacheSection)
  }

  override fun envRefOf(pyProject: PyProject, pythonBinary: Path): PyEnvRef =
    poetryEnvRefOf(PathHolder.Eel(pythonBinary))?.let(::PyEnvRef) ?: super.envRefOf(pyProject, pythonBinary)

  /** The interpreter of the env at [envRef]: the project's `.venv`, or the cache env of that Python version, on any machine. */
  override suspend fun <P : PathHolder> pythonBinaryOf(pyProject: PyProject, envRef: PyEnvRef, fileSystem: FileSystem<P>): PyResult<P> {
    val envRoot = poetryEnvRootOf(fileSystem, pyProject.baseDir, envRef.value).getOr { return it } ?: return envNotFound(envRef)
    return withContext(Dispatchers.IO) { fileSystem.resolvePythonBinary(envRoot) }?.let { PyResult.success(it) } ?: envNotFound(envRef)
  }

  override suspend fun envDirectory(pyProject: PyProject, envRef: PyEnvRef, fileSystem: FileSystemWithEel): Path? =
    poetryEnvRootOf(fileSystem, pyProject.baseDir, envRef.value).getOrNull()?.path

  /**
   * The cache environments of the project, as full env-root paths. It forces `virtualenvs.in-project=false`, as the v2
   * dialog does, so poetry lists the cache envs even when an in-project `.venv` exists. Otherwise it reports only
   * `.venv`. When poetry fails, the node shows no cache environments, and its rows still offer to create them.
   */
  private suspend fun cacheEnvRoots(context: EvoToolContext): List<Path> =
    context.cached(ENVS_KEY) { poetryCacheEnvRoots(context.fileSystem, context.workspace.baseDir).getOrNull()?.map { it.path }.orEmpty() }

  override suspend fun createInterpreter(context: EvoToolContext, ref: PyInterpreterRef): PyResult<PythonInterpreter> {
    // Not there yet, so poetry creates it, as hatch creates a declared environment.
    if (pythonBinaryOf(context.workspace.pyProject, ref.envRef, context.fileSystem).getOrNull() == null) return createEnv(context, ref)
    return super.createInterpreter(context, ref)
  }

  /**
   * Creates the environment that [ref] names. The in-project `.venv` is built on the best Python the project
   * admits, and a cache environment on the best installed Python of its own version.
   */
  private suspend fun createEnv(context: EvoToolContext, ref: PyInterpreterRef): PyResult<PythonInterpreter> {
    val poetryExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val baseDir = context.workspace.baseDir
    val inProject = ref.envRef.value == POETRY_IN_PROJECT_ENV_REF
    val option = context.cached(VERSIONS_KEY) { systemPythonOptions(baseDir, context.fileSystem) }
      .filter { !it.installable }
      .firstOrNull { inProject || it.title == poetryCacheEnvRefVersion(ref.envRef.value) }
                 ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.base.python.not.found", ref.envRef))
    return createNewPoetrySdk(
      moduleOrProject = context.workspace.moduleOrProject,
      moduleBasePath = baseDir,
      basePythonBinaryPath = PathHolder.Eel(Path.of(option.token)),
      fileSystem = context.fileSystem,
      poetryExecutable = poetryExecutable,
      installPackages = false,
      errorSink = context.errorSink,
      inProjectEnv = inProject,
      targetPanelExtension = null,
    )
  }

  /**
   * Creates a poetry env from the base Python in `token`, where the row that asked for it says.
   *
   * Only the in-project row wants `virtualenvs.in-project`; a per-version row leaves poetry to place the env in its own
   * cache. The two are told apart by `folder` naming the project's `.venv`, not by `folder` being set at all: the
   * frontend fills it with the row's own create token, and a per-version row's token is a base interpreter path. So
   * "is it set" answered yes for every row, and every environment was built in the project.
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: EvoRowAction.CreateEnv): PyResult<PythonInterpreter> {
    val poetryExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val baseDir = context.workspace.baseDir
    val inProject = ref.folder?.toNioPathOrNull()?.normalize() == defaultVenvDir(baseDir).normalize()
    return createNewPoetrySdk(
      moduleOrProject = context.workspace.moduleOrProject,
      moduleBasePath = baseDir,
      basePythonBinaryPath = PathHolder.Eel(Path.of(ref.token)),
      fileSystem = context.fileSystem,
      poetryExecutable = poetryExecutable,
      installPackages = false,
      errorSink = context.errorSink,
      inProjectEnv = inProject,
      targetPanelExtension = null,
    )
  }

  /**
   * A poetry environment may be rebuilt on any base Python the project's `requires-python` admits.
   *
   * The list is the one [addNewEnvSpec] memoized for this request, so the affordance costs no further probe. Poetry can
   * fill the rebuilt environment from `poetry.lock`, so the packages choice is offered — a full `poetry install`, which
   * can run for minutes.
   *
   * A cache row offers only the interpreters of its own version: the row *is* that version, so a rebuild there changes
   * which install backs it and nothing else. The in-project `.venv` stands for no version and offers them all.
   *
   * [recreateEnv] rebuilds either kind, each where it stands.
   */
  override suspend fun recreateSpecFor(context: EvoToolContext, leaf: EvoLeafDto): EvoRecreateDto? {
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(context.workspace.baseDir, context.fileSystem) }
      .takeIf { it.isNotEmpty() } ?: return null
    leaf.versionGroup?.let { version ->
      val own = options.firstOrNull { PySdkBundle.message("evolution.python.version", it.title) == version } ?: return null
      return EvoRecreateDto(options = listOf(own), canSyncPackages = true)
    }
    ownedEnvDirOf(context, leaf.action) ?: return null
    return EvoRecreateDto(options = options, canSyncPackages = true)
  }

  /**
   * Destroys the environment and lets poetry build another where that one stood.
   *
   * Both kinds of row reach this, and where the old environment stood is what decides everything below. The project's
   * `.venv` is a directory poetry owns and nothing else knows about, so deleting it is enough. A cache environment is
   * listed in poetry's own registry, so poetry removes it — deleting that directory would leave the registry naming an
   * environment that is no longer there.
   *
   * `virtualenvs.in-project` then puts the new environment back in the same place. Passing it for a cache environment
   * moved that environment into the project, which is not what a rebuild does.
   */
  override suspend fun recreateEnv(context: EvoToolContext, ref: PyInterpreterRef, spec: EvoRecreateSpec): PyResult<PythonInterpreter> {
    val poetryExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val projectDir = context.workspace.baseDir
    val envHome = envDirectory(context.workspace.pyProject, ref.envRef, context.fileSystem) ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", ref.envRef))
    val inProject = ref.envRef.value == POETRY_IN_PROJECT_ENV_REF
    if (inProject) {
      deleteEnvDir(envHome).getOr { return it }
    }
    else {
      // By the name poetry knows it by, which is the folder's own name — `poetry env list` prints exactly these.
      runPoetry(projectDir.asEelOrJustPath(), "env", "remove", envHome.name, inProjectEnv = false).getOr { return it }
    }
    return createNewPoetrySdk(
      moduleOrProject = context.workspace.moduleOrProject,
      moduleBasePath = projectDir,
      basePythonBinaryPath = PathHolder.Eel(Path.of(spec.baseToken)),
      fileSystem = context.fileSystem,
      poetryExecutable = poetryExecutable,
      installPackages = spec.syncPackages,
      errorSink = context.errorSink,
      inProjectEnv = inProject,
      targetPanelExtension = null,
    )
  }

  /**
   * Poetry's in-project env is always `.venv` — poetry ignores any other name — so the name is shown but not editable,
   * unlike uv's and pip's freely-named folders.
   */
  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    val baseDir = context.workspace.baseDir
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(baseDir, context.fileSystem) }
      .takeIf { it.isNotEmpty() } ?: return null
    val dir = defaultVenvDir(section.addNewFolderPath?.let { Path.of(it) } ?: baseDir)
    return EvoAddNewDto(name = dir.fileName.toString(), path = dir.pathString, options = options, nameEditable = false)
  }
}

/**
 * The version of the build an option would be created from: the one its token names, or the first offered when none
 * matches — a token that is a version to install rather than an interpreter path.
 */
private fun EvoAddNewOptionDto.defaultBaseVersion(): @NlsSafe String? =
  bases.firstOrNull { it.token == token }?.version ?: bases.firstOrNull()?.version

