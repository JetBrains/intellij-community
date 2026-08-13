// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.services

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.python.pytools.PyExecutable
import com.intellij.python.pytools.PyExecutableCache
import com.intellij.util.xmlb.annotations.OptionTag
import java.nio.file.Path

/**
 * Application-level, **per-Eel-machine** store of user-chosen executable paths for Python tools. Keyed
 * by [com.intellij.platform.eel.EelMachine.internalName] (e.g. `"Local"`, `"WSL-Ubuntu"`, `"SSH …"`)
 * and then by tool id (the tool's package name — `"uv"`, `"poetry"`, `"hatch"`, `"ruff"`, …), so each
 * tool remembers its own path independently on every machine a project might run on.
 *
 * Module-private: read by [PyExecutableCache] and written through `PyExecutable.setCustomExecutablePath`
 * (the External Tools / Package Managers settings pages) — no external code touches this store directly.
 * It replaces the former per-project `PyToolsState` custom path and the per-tool `PropertiesComponent`
 * keys (`PyCharm.Uv.Path` etc.), which are migrated in once via [noStateLoaded] and then deprecated.
 *
 * Absolute paths are host-specific, hence [RoamingType.LOCAL]. Entries are a best-effort cache: a
 * machine id can go stale (e.g. a recreated Docker container), so a missing/invalid entry is never an
 * error — callers fall back to auto-detection.
 */
@Service(Service.Level.APP)
@State(
  name = "PyToolCustomExecutablePaths",
  storages = [Storage("pyToolCustomExecutablePaths.xml", roamingType = RoamingType.LOCAL)],
)
internal class PyCustomExecutablePaths : PersistentStateComponent<PyCustomExecutablePaths.State> {
  class State {
    @get:OptionTag
    var machines: MutableMap<String, MachinePaths> = mutableMapOf()
  }

  class MachinePaths {
    @get:OptionTag
    var tools: MutableMap<String, String> = mutableMapOf()
  }

  private var state = State()

  override fun getState(): State = state
  override fun loadState(state: State) {
    this.state = state
  }

  /** The user-chosen custom path for [executable] on [eelDescriptor]'s machine, or `null`. */
  fun get(eelDescriptor: EelDescriptor, executable: PyExecutable): Path? {
    val machine = eelDescriptor.getResolvedEelMachine() ?: return null
    return getPath(machine.internalName, executable.fusId)
  }

  /**
   * Persist (or clear, when [path] is `null`) [executable]'s custom path on [eelDescriptor]'s machine.
   * Also drops the detection cache so the change takes effect immediately rather than after its TTL.
   */
  fun set(eelDescriptor: EelDescriptor, executable: PyExecutable, path: Path?) {
    val machine = eelDescriptor.getResolvedEelMachine() ?: return
    setPath(machine.internalName, executable.fusId, path)
    PyExecutableCache.getInstance().invalidate(eelDescriptor, executable)
  }

  @Synchronized
  private fun getPath(machineInternalName: String, toolId: String): Path? =
    state.machines[machineInternalName]?.tools?.get(toolId)?.let { Path.of(it) }

  @Synchronized
  private fun setPath(machineInternalName: String, toolId: String, path: Path?) {
    if (path == null) {
      val machine = state.machines[machineInternalName] ?: return
      machine.tools.remove(toolId)
      if (machine.tools.isEmpty()) state.machines.remove(machineInternalName)
    }
    else {
      state.machines.getOrPut(machineInternalName) { MachinePaths() }.tools[toolId] = path.toString()
    }
  }

  /**
   * One-time (first-run) migration of the legacy per-tool [PropertiesComponent] path keys into the
   * local machine's entry; the old keys are then cleared so the migration is one-way. Mirrors
   * `PyToolsState.noStateLoaded`.
   */
  @Synchronized
  override fun noStateLoaded() {
    val props = PropertiesComponent.getInstance()
    val local = LocalEelMachine.internalName
    for ((legacyKey, toolId) in LEGACY_LOCAL_PATH_KEYS) {
      val value = props.getValue(legacyKey)?.takeIf { it.isNotBlank() } ?: continue
      setPath(local, toolId, Path.of(value))
      props.unsetValue(legacyKey)
    }
  }

  companion object {
    fun getInstance(): PyCustomExecutablePaths = service()

    /** Legacy app-level path settings (key → tool id), migrated once into the local machine entry. */
    private val LEGACY_LOCAL_PATH_KEYS: List<Pair<String, String>> = listOf(
      "PyCharm.Uv.Path" to "uv",
      "PyCharm.Poetry.Path" to "poetry",
      "PyCharm.Pipenv.Path" to "pipenv",
      "PyCharm.Hatch.Local.Executable.Path" to "hatch",
      "PYCHARM_CONDA_FULL_LOCAL_PATH" to "conda",
    )
  }
}
