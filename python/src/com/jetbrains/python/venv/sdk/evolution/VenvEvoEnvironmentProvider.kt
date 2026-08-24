package com.jetbrains.python.venv.sdk.evolution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.createVenvFromSystemPython
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.envExistsError
import com.intellij.python.sdk.backend.evolution.firstFreeVenvDir
import com.intellij.python.sdk.backend.evolution.listEntryNames
import com.intellij.python.sdk.backend.evolution.resolveNewVenvDir
import com.intellij.python.sdk.backend.evolution.toSectionsGroupedByParent
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.venv.icons.PythonVenvIcons
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.createSdkGuessingTypeByPath
import com.jetbrains.python.sdk.impl.PySdkBundle
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Contributes the generic "pip" (virtualenv) node — plain venvs without a `uv` marker. Always available, since a
 * virtualenv needs no tool beyond a Python, which is why this implements [PyEvoEnvironmentProvider] directly rather than
 * extending the `PyTool`-backed base class: there is no pip *tool* to detect.
 *
 * It lives here rather than in `intellij.python.venv` because creating a venv from a system Python
 * ([createVenvFromSystemPython]) sits *above* that module by design — `services.systemPython` depends on it — so a
 * provider there could not reach its own creation logic without a cycle.
 */
internal class VenvEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val toolId: ToolId get() = VENV_TOOL_ID
  override val label: String get() = "pip"
  override val icon: Icon get() = PythonVenvIcons.VirtualEnv

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto =
    EvoLoadResultDto.Ok(discovered.filterNot { it.createdByUv }.toSectionsGroupedByParent(icon, addNew = true, baseDir = pyProject.baseDir))

  /**
   * A plain virtualenv has no tool-specific SDK, so its type is guessed from the path — the generic route the core used
   * to take on this node's behalf. Owning it here means a failure is reported instead of silently yielding some other
   * kind of SDK.
   */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> =
    createSdkGuessingTypeByPath(
      PathHolder.Eel(homePath),
      context.fileSystem,
      ModuleOrProject.ModuleAndProject(context.pyProject.module),
      null,
    )

  /** Creates a virtualenv from the system Python named by `token`, then types its SDK by path. */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val venvDir = context.resolveNewVenvDir(ref)
    if (venvDir.exists()) return envExistsError(venvDir.fileName.toString())
    val eelApi = context.fileSystem.eelDescriptor?.toEelApi() ?: localEel
    val systemPython = SystemPythonService().findSystemPythons(eelApi).firstOrNull { it.pythonBinary.pathString == ref.token }
                       ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.base.python.not.found", ref.token))
    val venvPython = createVenvFromSystemPython(systemPython, venvDir).getOr { return it }
    return createSdkGuessingTypeByPath(
      PathHolder.Eel(venvPython),
      context.fileSystem,
      ModuleOrProject.ModuleAndProject(context.pyProject.module),
      null,
    )
  }

  /**
   * The env folder is created inside the section's containing directory, named by the first free `.venv{X}` there and
   * editable — the same shape uv offers, since both create a folder rather than a named environment.
   *
   * Taken names are *every* existing entry in that directory, not only virtualenvs: any file or folder of the same name
   * would block creating the env there.
   */
  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    val options = context.systemPythonOptions().takeIf { it.isNotEmpty() } ?: return null
    val container = section.addNewFolderPath?.let { Path.of(it) } ?: context.pyProject.baseDir
    val taken = listEntryNames(container)
    return EvoAddNewDto(
      name = firstFreeVenvDir(container).fileName.toString(),
      path = container.pathString,
      options = options,
      nameEditable = true,
      takenNames = taken,
    )
  }
}
