// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.serviceContainer.AlreadyDisposedException
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonInterpreterListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * What the SDK configurators say about one project: the virtualenvs found in it, and the options they
 * offer, best-first.
 *
 * The two travel together because they are produced together and both are needed together — a caller
 * that picks an option from [options] may also need [venvs] to ask a configurator something
 * further (see `getModuleSdkStateImpl`), and scanning for venvs twice would defeat the point of caching.
 */
@ApiStatus.Internal
data class PyProjectConfigurators(
  val venvs: List<PythonBinary>,
  val options: List<CreateSdkInfoWithTool>,
)

/** How long a project's detected configurators are reused. Matches the interpreter notification's own cache. */
private val TTL = 20.seconds

/** The probe of a module, which the module keeps in its user data. [ended] is set when the probe succeeds. */
private class ConfiguratorsEntry(val probe: Deferred<PyProjectConfigurators>) {
  @Volatile
  var ended: TimeMark? = null

  val isExpired: Boolean get() = ended?.let { it.elapsedNow() >= TTL } ?: false
}

private val ENTRY_KEY = Key.create<ConfiguratorsEntry>("PySdkConfiguratorsCache.entry")

/**
 * The one place the SDK configurators are asked what they can do for a project.
 *
 * Answering that question is expensive: every configurator gets to probe the file system and *run its
 * tool* — `poetry check --lock`, `uv python list`, and so on (see
 * [PyProjectSdkConfigurationExtension.checkEnvironmentAndPrepareSdkCreator]). The answer is also asked
 * for constantly and from unrelated places: the interpreter widget's shortcut rows, the "no interpreter
 * configured" notification, the Add Interpreter dialog's tool preselection, the MCP environment tool,
 * and the auto-configuration that runs when a project opens. Before this cache each of them paid for
 * its own probe, so opening one poetry project ran `poetry check --lock` several times over.
 *
 * The module of a project keeps the probe in its user data, so the answer ends with the module.
 *
 * Callers that need an answer which is true *right now* must not come here — see
 * [PyProjectSdkConfigurationExtension.findAllSorted], which stays uncached for exactly that.
 */
@Service(Service.Level.PROJECT)
internal class PySdkConfiguratorsCache(private val project: Project, private val scope: CoroutineScope) {
  init {
    // An SDK appearing is the one event that makes every answer here obsolete at once: with an SDK the
    // project needs no configuring, and until then the options are what the probes found. PythonPluginDisposable
    // parents the connection so it goes away on plugin unload.
    ApplicationManager.getApplication().messageBus.connect(PythonPluginDisposable.getInstance(project)).subscribe(PythonInterpreterListener.TOPIC, object : PythonInterpreterListener {
      override fun moduleInterpreterChanged(module: Module, previous: PythonInterpreter?, current: PythonInterpreter?) {
        // PythonInterpreterListener fires on the application bus, so this is told about every open project's modules.
        // Dropping another project's entry would be someone else's cache to clear (cf. PY-91324).
        if (module.project == project) invalidate(module)
      }
    })
  }

  private val lock = Any()

  /**
   * The cached answer for [pyProject], probing only if no running or recent probe exists.
   *
   * The callers that arrive together while a project opens share a single probe rather than each starting one.
   * The probe runs on this service's scope, so a cancelled caller stops to wait, and the probe continues for the rest.
   * A probe that fails is not kept, and the next caller starts a new one.
   */
  suspend fun get(pyProject: PyProject): PyProjectConfigurators = probeOf(pyProject).await()

  /** Drops [module]'s entry, for a caller that has just changed what a probe would find (installing a tool). */
  fun invalidate(module: Module) {
    synchronized(lock) { module.putUserData(ENTRY_KEY, null) }
  }

  private fun probeOf(pyProject: PyProject): Deferred<PyProjectConfigurators> {
    val module = pyProject.residesOnModule
    synchronized(lock) {
      module.getUserData(ENTRY_KEY)?.takeUnless { it.isExpired }?.let { return it.probe }
      val entry = ConfiguratorsEntry(scope.async { probe(pyProject) })
      module.putUserData(ENTRY_KEY, entry)
      entry.probe.invokeOnCompletion { error ->
        if (error == null) entry.ended = TimeSource.Monotonic.markNow() else drop(module, entry)
      }
      return entry.probe
    }
  }

  private fun drop(module: Module, entry: ConfiguratorsEntry) {
    synchronized(lock) {
      if (module.getUserData(ENTRY_KEY) === entry) module.putUserData(ENTRY_KEY, null)
    }
  }

  private suspend fun probe(pyProject: PyProject): PyProjectConfigurators {
    // The automatic configuration walks every project on a scope of its own, and a project can close under it. A
    // disposed module is not probed: that would run the tool of each configurator for something that is gone. The
    // refusal is the cancellation that the walk handles.
    val module = pyProject.residesOnModule
    if (module.isDisposed) throw AlreadyDisposedException("Module $module is disposed, its configurators are not probed")
    val venvs = pyProject.findPythonVirtualEnvironments()
    return PyProjectConfigurators(venvs, PyProjectSdkConfigurationExtension.findAllSorted(pyProject, venvs))
  }

  companion object {
    fun getInstance(project: Project): PySdkConfiguratorsCache = project.service()
  }
}
