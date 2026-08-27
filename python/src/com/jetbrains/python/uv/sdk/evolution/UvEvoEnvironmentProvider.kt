package com.jetbrains.python.uv.sdk.evolution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.envExistsError
import com.intellij.python.sdk.backend.evolution.firstFreeVenvDir
import com.intellij.python.sdk.backend.evolution.listEntryNames
import com.intellij.python.sdk.backend.evolution.resolveNewVenvDir
import com.intellij.python.sdk.backend.evolution.toSectionsGroupedByParent
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.uv.backend.PyUvBundle
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.backend.cli.uv.UvPythonEntry
import com.intellij.python.uv.backend.runtime.uvCli
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.evolution.requiresPython
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import com.jetbrains.python.sdk.uv.UvSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

private const val VERSIONS_KEY: String = "uv.supportedPythonVersions"

internal class UvEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = UvPyTool.getInstance()
  override val toolId: ToolId get() = UV_TOOL_ID

  /** An interpreter of this node's environments carries this flavor, which is what names this node as the active one. */
  override val sdkFlavor: Class<out PythonSdkFlavor<*>> get() = UvSdkFlavor::class.java

  /**
   * `uv venv` writes its own version into the environment's `pyvenv.cfg`, which is what lets every node say that uv made
   * it. uv is the only one of the tools here that leaves such a mark.
   */
  override val pyvenvMarker: String get() = "uv"

  // uv works with any virtualenv, so it shows all discovered environments.
  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto =
    EvoLoadResultDto.Ok(discovered.toSectionsGroupedByParent(this, addNew = true, baseDir = pyProject.baseDir))

  /**
   * Adopts an existing virtualenv as a uv env — `usePip = false`, so the SDK is wired to uv rather than to pip even
   * though the env itself is an ordinary virtualenv either tool could claim.
   */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> {
    val uvPath = executableOrNull(context.fileSystem) ?: return toolMissing()
    return setupExistingEnvAndSdk(
      pythonBinary = PathHolder.Eel(homePath),
      uvPath = uvPath,
      workingDir = context.pyProject.baseDir,
      fileSystem = context.fileSystem,
      usePip = false,
    )
  }

  /**
   * Creates a new uv env in the folder the add-new row named; `token` is the chosen Python version as `major.minor`
   * ("" = uv's default), which is all `uv venv --python` is given — see [supportedPythonVersions].
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val uvExecutable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val venvDir = context.resolveNewVenvDir(ref)
    if (venvDir.exists()) return envExistsError(venvDir.fileName.toString())
    // The token is a version this provider itself serialized in addNewEnvSpec, so a malformed one is a bug in the
    // round-trip rather than bad user input — say so instead of silently falling back to uv's default Python.
    val version = ref.token.takeIf { it.isNotBlank() }?.let { token ->
      try {
        Version.parse(token, strict = false)
      }
      catch (e: VersionFormatException) {
        return PyResult.localizedError(PySdkBundle.message("evolution.error.bad.python.version", token, e.message ?: ""))
      }
    }
    return setupNewUvSdkAndEnv(
      uvExecutable = uvExecutable,
      workingDir = context.pyProject.baseDir,
      venvPath = PathHolder.Eel(venvDir),
      fileSystem = context.fileSystem,
      version = version,
      errorSink = context.errorSink,
    )
  }

  /**
   * The env folder is created inside the section's containing directory, named by the first free `.venv{X}` there and
   * editable.
   *
   * Taken names are *every* existing entry in that directory, not only virtualenvs: any file or folder of the same name
   * would block creating the env there, so the name field has to reject all of them.
   */
  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    // Probed once per request, not once per section: a node with several folders offers the same versions in each.
    val versions = context.cached(VERSIONS_KEY) { supportedPythonVersions(context) }.takeIf { it.isNotEmpty() } ?: return null
    val container = section.addNewFolderPath?.let { Path.of(it) } ?: context.pyProject.baseDir
    val taken = listEntryNames(container)
    return EvoAddNewDto(
      name = firstFreeVenvDir(container).fileName.toString(),
      path = container.pathString,
      options = versions,
      nameEditable = true,
      takenNames = taken,
    )
  }

  /**
   * The Python versions uv itself offers, filtered by the project's `requires-python`, newest first.
   *
   * Read from `uv python list --output-format json` (see [UvPythonEntry]) rather than from uv's table, so a version the
   * machine does not have is recognizable as one uv would fetch instead of looking like one already here.
   *
   * Entries are grouped down to `major.minor` because that is the granularity uv is actually asked for:
   * `UvLowLevel.initializeEnvironment` passes `--python <major>.<minor>`, so within one such group the choice of build is
   * uv's to make, not the user's.
   */
  private suspend fun supportedPythonVersions(context: EvoToolContext): List<EvoAddNewOptionDto> {
    val uvExecutable = executableOrNull(context.fileSystem) ?: return emptyList()
    val baseDir = context.pyProject.baseDir
    // Run in the project dir, as the rest of this provider's uv calls do: uv reads `.python-version` and the project's
    // own `pyproject.toml` from its working directory, so listing from anywhere else can name a different set.
    val runtime = PyToolRuntime(
      binary = context.fileSystem.getBinaryToExec(uvExecutable, baseDir),
      execOptions = ExecOptions(),
    )
    // Handed over exactly as `pyproject.toml` wrote it: uv parses the constraint itself, so what it makes of one is its
    // answer to give. An empty answer is what [decorate] turns into the node's warning.
    val entries = runtime.uvCli().python().list(requiresPython(baseDir)).getOrNull().orEmpty()
    return entries
      .groupBy { it.versionParts.major to it.versionParts.minor }
      .entries
      .sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, List<UvPythonEntry>>> { it.key.first }
                    .thenByDescending { it.key.second })
      .map { (_, group) -> group.toOption() }
  }

  /**
   * Turns "uv named no interpreter" into the node's warning, since nothing can be created from an empty list — saying so
   * up front beats an "add new environment" row that would only fail once clicked.
   *
   * The warning deliberately does not claim a cause. uv answers an empty list both for a constraint nothing satisfies
   * (`3.16`, which does not exist yet) and for one it cannot parse (a Poetry-style `^3.13`, which also fails `uv sync`),
   * and the two are indistinguishable from here: uv reports both as success with no rows.
   *
   * Runs off the same per-request cache [addNewEnvSpec] fills, so this costs no extra uv call.
   */
  override suspend fun decorate(context: EvoToolContext, result: EvoLoadResultDto): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    if (context.cached(VERSIONS_KEY) { supportedPythonVersions(context) }.isNotEmpty()) return result
    val requiresPython = requiresPython(context.pyProject.baseDir)
    return EvoLoadResultDto.Warning(
      if (requiresPython == null) PyUvBundle.message("evolution.uv.no.pythons")
      else PyUvBundle.message("evolution.uv.no.pythons.for.requires", requiresPython)
    )
  }
}

/** All the interpreters uv listed for one `major.minor`, as the single option that version amounts to. */
private fun List<UvPythonEntry>.toOption(): EvoAddNewOptionDto {
  val level = first().versionParts.languageLevel
  return EvoAddNewOptionDto(
    title = level,
    // Not the full `3.14.5`: only major.minor survives into `uv venv --python`, so pinning a patch here would promise
    // a precision the create step drops. See `UvLowLevel.initializeEnvironment`.
    token = level,
    // uv fetches what it cannot find, so a version with nothing installed at all is a download — but one uv performs
    // itself, which is why this is not `installable` (that would run the IDE's own Python installer first, to no purpose).
    downloadedByTool = none { it.path != null },
  )
}
