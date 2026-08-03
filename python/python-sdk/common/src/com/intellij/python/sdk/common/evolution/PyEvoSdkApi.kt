package com.intellij.python.sdk.common.evolution

import com.intellij.ide.ui.icons.IconId
import com.intellij.platform.project.ProjectId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Split-mode wire contract for the "Evo" Python interpreter widget
 * (registry `python.evolution.widget`).
 *
 * All Python-SDK discovery is backend-only, so the frontend widget/popup obtain
 * their display data through these read-only RPC calls and render it into UI.
 * A module is referenced by [ProjectId] plus its module name (resolved on the backend).
 */
@ApiStatus.Internal
@Rpc
interface PyEvoSdkApi : RemoteApi<Unit> {
  /** The interpreter currently configured for the module, as display-ready data (or `null` if none). */
  suspend fun getCurrentSdk(projectId: ProjectId, moduleName: String): EvoSdkDto?

  /**
   * The expandable "select environment" nodes contributed by the backend `evoTreeElementProvider`
   * extension point (pip/uv/poetry/conda/hatch/autoconfigure/…), shown collapsed in the popup.
   */
  suspend fun listNodes(projectId: ProjectId, moduleName: String): List<EvoNodeDto>

  /** Lazily loads the sections of the node with [nodeId] (from a backend provider) when it is expanded. */
  suspend fun loadNode(projectId: ProjectId, moduleName: String, nodeId: String): EvoLoadResultDto
}

@ApiStatus.Internal
suspend fun PyEvoSdkApi(): PyEvoSdkApi = RemoteApiProviderService.resolve(remoteApiDescriptor<PyEvoSdkApi>())

/**
 * Frontend-facing wrappers that hide the RPC/`fleet.rpc` types behind plain DTO results, so the frontend
 * module does not need to depend on `intellij.platform.rpc`.
 */
@ApiStatus.Internal
suspend fun requestEvoCurrentSdk(projectId: ProjectId, moduleName: String): EvoSdkDto? =
  PyEvoSdkApi().getCurrentSdk(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoNodes(projectId: ProjectId, moduleName: String): List<EvoNodeDto> =
  PyEvoSdkApi().listNodes(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoNode(projectId: ProjectId, moduleName: String, nodeId: String): EvoLoadResultDto =
  PyEvoSdkApi().loadNode(projectId, moduleName, nodeId)

/** Frontend-safe, serializable replacement for the former `EvoSdk` UI model. */
@ApiStatus.Internal
@Serializable
data class EvoSdkDto(
  val target: String = "local",
  val name: String?,
  /** Display-only interpreter path (not resolved on the frontend). */
  val pythonBinaryPath: String?,
  /** Pre-computed on the backend so the frontend never spawns a process. */
  val pythonVersion: String?,
  /** RPC-transferable icon; the contributing provider supplies it via `Icon.rpcId()`. */
  val icon: IconId,
  val options: List<EvoSdkOptionDto> = emptyList(),
)

@ApiStatus.Internal
@Serializable
enum class EvoSdkOptionDto { SUDO }

/** A collapsed expandable node in the popup's "select environment" section, contributed by a backend provider. */
@ApiStatus.Internal
@Serializable
data class EvoNodeDto(
  val id: String,
  val label: @Nls String,
  val icon: IconId,
)

/** One leaf row inside a loaded node. Actions are display-only stubs today (no switching yet). */
@ApiStatus.Internal
@Serializable
data class EvoLeafDto(
  val title: @Nls String,
  val description: @Nls String? = null,
  val secondaryText: @Nls String? = null,
  val icon: IconId,
  val kind: EvoLeafKind,
  /** The interpreter this row selects, when [kind] is [EvoLeafKind.SELECT_ENV]. */
  val sdk: EvoSdkDto? = null,
)

@ApiStatus.Internal
@Serializable
enum class EvoLeafKind {
  /** Selects an interpreter ([EvoLeafDto.sdk] is set). */
  SELECT_ENV,

  /** A labeled, display-only action row (autoconfigure options, advanced add-interpreter actions, …). */
  ACTION,
}

@ApiStatus.Internal
@Serializable
data class EvoSectionDto(
  val label: @Nls String? = null,
  val leaves: List<EvoLeafDto>,
  /** When true, the frontend appends its localized "Add new environment" row to this section. */
  val addNew: Boolean = false,
)

/** Result of [PyEvoSdkApi.loadNode]: sections on success, or a warning/critical error message. */
@ApiStatus.Internal
@Serializable
sealed interface EvoLoadResultDto {
  @Serializable data class Ok(val sections: List<EvoSectionDto>) : EvoLoadResultDto
  @Serializable data class Warning(val message: @Nls String) : EvoLoadResultDto
  @Serializable data class Error(val message: @Nls String) : EvoLoadResultDto
}

/** Pure, frontend-safe formatting of an interpreter's address (schema + name + options). Shared by both sides. */
@ApiStatus.Internal
fun EvoSdkDto.getAddress(): @NlsSafe String {
  val schema = if (target != "local") "$target://" else ""
  val nm = name ?: ""
  val opts = options.joinToString("") { option ->
    when (option) {
      EvoSdkOptionDto.SUDO -> " --sudo"
    }
  }
  return "$schema$nm$opts"
}
