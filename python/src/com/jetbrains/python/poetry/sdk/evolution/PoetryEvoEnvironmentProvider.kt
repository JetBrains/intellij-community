package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.pytools.PyTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoInstallPythonLeaf
import com.intellij.python.sdk.backend.evolution.ownedEnvBinaryIn
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.toLeaf
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.evolution.deleteEnvDir
import com.jetbrains.python.sdk.evolution.systemPythonOptions
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.impl.resolvePythonHome
import com.jetbrains.python.sdk.poetry.createNewPoetrySdk
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.poetry.runPoetry
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import com.jetbrains.python.sdk.poetry.PyPoetrySdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

private const val VERSIONS_KEY: String = "poetry.systemPythons"

internal class PoetryEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = PoetryPyTool.getInstance()
  override val toolId: ToolId get() = POETRY_TOOL_ID

  /** An interpreter of this node's environments carries this flavor, which is what names this node as the active one. */
  override val sdkFlavor: Class<out PythonSdkFlavor<*>> get() = PyPoetrySdkFlavor::class.java

  override val stepDescription: String get() = PySdkBundle.message("evolution.node.step.poetry")

  /**
   * The project's own `.venv`, and nothing that costs a Python scan.
   *
   * The cache rows are built in [decorate] instead, which is handed the context and so can share its memoized interpreter
   * list. Built here they went through the uncached entry point and made the node scan the machine twice — once for
   * these rows and once for the choices every row now carries.
   */
  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val projectDir = pyProject.baseDir
    // Exactly the project's `.venv` — poetry's only in-project location, it can't be `.venv1` nor more than one. Shown
    // if it exists, even when poetry did not create it, and then no "add new"; otherwise the row that creates it.
    val inProjectVenv = discovered.firstOrNull { it.venvRoot == defaultVenvDir(projectDir) }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(
      label = PySdkBundle.message("evolution.poetry.in.project"),
      leaves = listOfNotNull(inProjectVenv?.toLeaf(this)),
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
    val projectDir = context.pyProject.baseDir
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(projectDir, context.fileSystem) }
    if (options.isEmpty()) return result
    // Poetry's cache environments, as full env-root paths. Force `virtualenvs.in-project=false` (as the v2 dialog does)
    // so poetry enumerates the cache envs even when an in-project `.venv` exists — otherwise it reports only `.venv`.
    val poetryEnvRoots: List<Path> = runPoetry(projectDir, "env", "list", "--full-path", inProjectEnv = false).getOrNull()
      ?.lineSequence()
      ?.map { Path.of(it.removeSuffix("(Activated)").trim()) }
      ?.filter { it.name.isNotBlank() }
      ?.toList()
      ?: emptyList()

    val perVersionLeaves = options.map { option ->
      val versionStr = option.title
      // These rows are identified by the Python they hold rather than by an env name, so spell that out the way the
      // add-new version rows do ("Python 3.13") instead of showing a bare number. Only the label changes: the lookup
      // below still matches on the plain version, which is what poetry puts at the end of the cache env's folder name.
      val title = PySdkBundle.message("evolution.python.version", versionStr)
      val existingBinary = poetryEnvRoots.firstOrNull { it.name.endsWith(versionStr) }?.resolvePythonBinary()
      val leaf = when {
        // Not on the machine: offer to install it. Its token is the version rather than an interpreter path, so it
        // cannot be handed to the create step as-is — evoInstallPythonLeaf is what asks for the install first.
        option.installable -> evoInstallPythonLeaf(title = title, version = versionStr)
        existingBinary != null -> evoEnvLeaf(title = title, pythonBinary = existingBinary, icon = icon)
        // Built from the best interpreter of that version, with the others behind the row's own pencil rather than
        // behind a view of the whole node — see [recreateSpecFor].
        else -> evoCreateEnvLeaf(title = title, token = option.token, icon = icon)
          .copy(createVersions = listOf(option), secondaryText = option.bases.firstOrNull()?.version)
      }
      leaf.copy(versionGroup = title)
    }
    // Headed by where poetry keeps these rather than by the directory it keeps them in. The directory would cost a
    // `poetry config virtualenvs.path` run of its own — a whole poetry start-up for a heading that says no more.
    val cacheSection = EvoSectionDto(label = PySdkBundle.message("evolution.poetry.in.caches"), leaves = perVersionLeaves)
    return result.copy(sections = result.sections + cacheSection)
  }

  /** Adopts an existing poetry env (in-project `.venv` or a cache env) as a poetry-typed SDK. */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> =
    createPoetrySdk(context.pyProject.baseDir, PathHolder.Eel(homePath), context.fileSystem)

  /**
   * Creates a poetry env from the base Python in `token`, where the row that asked for it says.
   *
   * Only the in-project row wants `virtualenvs.in-project`; a per-version row leaves poetry to place the env in its own
   * cache. The two are told apart by `folder` naming the project's `.venv`, not by `folder` being set at all: the
   * frontend fills it with the row's own create token, and a per-version row's token is a base interpreter path. So
   * "is it set" answered yes for every row, and every environment was built in the project.
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val poetryExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val baseDir = context.pyProject.baseDir
    val inProject = ref.folder?.toNioPathOrNull()?.normalize() == defaultVenvDir(baseDir).normalize()
    return createNewPoetrySdk(
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
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(context.pyProject.baseDir, context.fileSystem) }
      .takeIf { it.isNotEmpty() } ?: return null
    leaf.versionGroup?.let { version ->
      val own = options.firstOrNull { PySdkBundle.message("evolution.python.version", it.title) == version } ?: return null
      return EvoRecreateDto(options = listOf(own), canSyncPackages = true)
    }
    leaf.ref.ownedEnvBinaryIn(context.pyProject.baseDir) ?: return null
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
  override suspend fun recreateEnv(context: EvoToolContext, homePath: Path, spec: EvoRecreateSpec): PyResult<Sdk> {
    val poetryExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val projectDir = context.pyProject.baseDir
    val envHome = homePath.resolvePythonHome()
    val inProject = envHome.normalize().startsWith(projectDir.normalize())
    if (inProject) {
      deleteEnvDir(envHome).getOr { return it }
    }
    else {
      // By the name poetry knows it by, which is the folder's own name — `poetry env list` prints exactly these.
      runPoetry(projectDir, "env", "remove", envHome.name, inProjectEnv = false).getOr { return it }
    }
    return createNewPoetrySdk(
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
    val baseDir = context.pyProject.baseDir
    val options = context.cached(VERSIONS_KEY) { systemPythonOptions(baseDir, context.fileSystem) }
      .takeIf { it.isNotEmpty() } ?: return null
    val dir = defaultVenvDir(section.addNewFolderPath?.let { Path.of(it) } ?: baseDir)
    return EvoAddNewDto(name = dir.fileName.toString(), path = dir.pathString, options = options, nameEditable = false)
  }
}
