package com.jetbrains.python.uv.sdk.evolution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pytools.PyTool
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
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.evolution.requiresPython
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.validateAndCreateUvCli
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

private const val VERSIONS_KEY: String = "uv.supportedPythonVersions"

internal class UvEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = UvPyTool.getInstance()
  override val toolId: ToolId get() = UV_TOOL_ID

  // uv works with any virtualenv, so it shows all discovered environments.
  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto =
    EvoLoadResultDto.Ok(discovered.toSectionsGroupedByParent(icon, addNew = true, baseDir = pyProject.baseDir))

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

  /** Creates a new uv env in the folder the add-new row named; `token` is the chosen Python version ("" = uv's default). */
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
   * The Python versions uv itself offers, filtered by the project's `requires-python` — the same list the v2 dialog
   * shows, newest first. The token is the full version so the create step can pin it.
   */
  private suspend fun supportedPythonVersions(context: EvoToolContext): List<EvoAddNewOptionDto> {
    val uvExecutable = executableOrNull(context.fileSystem) ?: return emptyList()
    val cli = validateAndCreateUvCli(uvExecutable, context.fileSystem).getOrNull() ?: return emptyList()
    val baseDir = context.pyProject.baseDir
    val versions = createUvLowLevel(baseDir, cli, context.fileSystem, null)
      .listSupportedPythonVersions(requiresPython(baseDir)).getOrNull().orEmpty()
    return versions.map { EvoAddNewOptionDto(title = "${it.major}.${it.minor}", token = it.toString()) }
  }
}
