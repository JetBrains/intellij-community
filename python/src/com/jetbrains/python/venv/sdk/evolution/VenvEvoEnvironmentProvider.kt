package com.jetbrains.python.venv.sdk.evolution

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.venv.createVenv
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.services.systemPython.createVenvFromSystemPython
import com.intellij.python.pytools.PyTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.envExistsError
import com.intellij.python.sdk.backend.evolution.firstFreeVenvDir
import com.intellij.python.sdk.backend.evolution.listEntryNames
import com.intellij.python.sdk.backend.evolution.ownedEnvBinaryIn
import com.intellij.python.sdk.backend.evolution.resolveNewVenvDir
import com.intellij.python.sdk.backend.evolution.toLeaf
import com.intellij.python.sdk.backend.evolution.toSectionsGroupedByParent
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.venv.PipPyTool
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.createSdkGuessingTypeByPath
import com.jetbrains.python.sdk.evolution.deleteEnvDir
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonHome
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

/**
 * Contributes the generic "pip" (virtualenv) node — every virtualenv the discovery found. Always available, since a
 * virtualenv needs no tool beyond a Python, which is why this implements [PyEvoEnvironmentProvider] directly rather than
 * extending the `PyTool`-backed base class: there is no pip *tool* to detect.
 *
 * It lives here rather than in `intellij.python.venv` because creating a venv from a system Python
 * ([createVenvFromSystemPython]) sits *above* that module by design — `services.systemPython` depends on it — so a
 * provider there could not reach its own creation logic without a cycle.
 */
internal class VenvEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  /**
   * The tool this node speaks for. Unlike the tool-backed providers it does not extend
   * `PyToolEvoEnvironmentProvider`, because this node is *always* available rather than available when an executable
   * resolves — but it still takes its name and its statistics identity from the tool rather than spelling them out.
   */
  private val tool: PyTool get() = PipPyTool.getInstance()

  override val toolId: ToolId get() = VENV_TOOL_ID

  /** An interpreter of this node's environments carries this flavor, which is what names this node as the active one. */
  override val sdkFlavor: Class<out PythonSdkFlavor<*>> get() = VirtualEnvSdkFlavor::class.java
  override val nodeKind: EvoNodeKind get() = EvoNodeKind.TOOL
  override val label: String get() = tool.presentableName
  override val fusId: String get() = tool.fusId
  override val icon: Icon get() = tool.icon

  /**
   * Every discovered virtualenv, one made by another tool included.
   *
   * This node used to hide a uv-made environment, which left a project whose only environment came from uv with an empty
   * pip node. It is listed instead, drawn with the icon of the tool that made it and disabled there, so the environment
   * is visible where the user looks for it and is still adopted on the node of the tool that manages it.
   *
   * The node claims no mark of its own: it creates its environments with the standard library's `venv`, which writes
   * nothing into `pyvenv.cfg` to say so. An environment naming no tool therefore reads as this node's own.
   */
  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val inProject = defaultVenvDir(pyProject.baseDir)
    val existing = discovered.firstOrNull { it.venvRoot == inProject }
    // The project's own `.venv` leads the list under its own heading, whether it is there yet or not — the same shape
    // Poetry's and uv's nodes have. Headed by what the environment *is* to the project rather than by the folder that
    // holds it, which for this one row is the project directory and says nothing the row does not.
    val inProjectSection = EvoSectionDto(
      label = PySdkBundle.message("evolution.section.in.project"),
      leaves = listOf(existing?.toLeaf(this)
                      ?: evoCreateEnvLeaf(title = inProject.name, token = inProject.name, icon = icon)),
    )
    val elsewhere = discovered.filter { it.venvRoot != inProject }
      .toSectionsGroupedByParent(this, addNew = false, baseDir = pyProject.baseDir)
    return EvoLoadResultDto.Ok(listOf(inProjectSection) + elsewhere)
  }

  override val stepDescription: String get() = PySdkBundle.message("evolution.node.step.pip")

  /** The system Pythons a not-yet-created environment could be built on — see `UvEvoEnvironmentProvider.decorate`. */
  override suspend fun decorate(context: EvoToolContext, result: EvoLoadResultDto): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    val options = context.systemPythonOptions().takeIf { it.isNotEmpty() } ?: return result
    return result.copy(sections = result.sections.map { section ->
      section.copy(leaves = section.leaves.map { leaf ->
        if (leaf.ref is PyInterpreterRef.CreateEnv) leaf.copy(createVersions = options) else leaf
      })
    })
  }

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
    return createVenvIn(context, venvDir, ref.token)
  }

  /**
   * Every virtualenv on this node may be rebuilt, on any system Python the machine has.
   *
   * The list is the one [addNewEnvSpec] offers, since a rebuild picks its base the same way a create does. No packages
   * choice: pip has no lock file to read one from, and a `requirements.txt` lying beside the project is a guess rather
   * than a record of what the environment held.
   *
   * This node keeps no record of what it made — a plain virtualenv names no tool — so its only way to tell an
   * environment it may destroy from one it merely found is where the environment sits. Hence [ownedEnvBinaryIn]: an
   * interpreter outside the project belongs to something else, whatever put it there.
   */
  override suspend fun recreateSpecFor(context: EvoToolContext, leaf: EvoLeafDto): EvoRecreateDto? {
    leaf.ref.ownedEnvBinaryIn(context.pyProject.baseDir) ?: return null
    val options = context.systemPythonOptions().takeIf { it.isNotEmpty() } ?: return null
    return EvoRecreateDto(options = options, canSyncPackages = false)
  }

  /**
   * Deletes the environment and builds another in its place, since `venv` has no command that replaces one.
   *
   * The delete comes first and its failure ends this: building over a directory that refused to go would leave the two
   * environments mixed together in one folder.
   */
  override suspend fun recreateEnv(context: EvoToolContext, homePath: Path, spec: EvoRecreateSpec): PyResult<Sdk> {
    val venvDir = homePath.resolvePythonHome()
    deleteEnvDir(venvDir).getOr { return it }
    return createVenvIn(context, venvDir, spec.baseToken)
  }

  /**
   * Creates a virtualenv in [venvDir] from the interpreter at [baseToken], then types its SDK by path.
   *
   * The interpreter is used as the path it is. It used to be looked up in the machine-wide interpreter scan first, only
   * for that scan's answer to be unwrapped back to the same path — `createVenvFromSystemPython` reads nothing else off
   * it — and an interpreter the scan did not list could then not build an environment at all, however the user had come
   * by it. `createVenv` validates the interpreter itself, so nothing was checked there either.
   */
  private suspend fun createVenvIn(context: EvoToolContext, venvDir: Path, baseToken: String): PyResult<Sdk> {
    val basePython = baseToken.toNioPathOrNull()
                     ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.base.python.not.found", baseToken))
    val venvPython = createVenv(basePython, venvDir).getOr { return it }
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
