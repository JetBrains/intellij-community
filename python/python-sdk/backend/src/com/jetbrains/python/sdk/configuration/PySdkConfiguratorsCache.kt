// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.python.pytools.backend.ExpiringSharedLoad
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.serviceContainer.AlreadyDisposedException
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PythonInterpreterListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

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
 * The module of a project keeps the probe in its user data. The removal or the unload of the module cancels its probe.
 *
 * Callers that need an answer which is true *right now* must not come here — see
 * [PyProjectSdkConfigurationExtension.findAllSorted], which stays uncached for exactly that.
 */
@Service(Service.Level.PROJECT)
internal class PySdkConfiguratorsCache(private val project: Project, private val scope: CoroutineScope) {
  init {
    val disposable = PythonPluginDisposable.getInstance(project)
    // An SDK appearing is the one event that makes every answer here obsolete at once: with an SDK the
    // project needs no configuring, and until then the options are what the probes found.
    val applicationBus = ApplicationManager.getApplication().messageBus
    applicationBus.connect(disposable).subscribe(PythonInterpreterListener.TOPIC, object : PythonInterpreterListener {
      override fun moduleInterpreterChanged(module: Module, previous: PythonInterpreter?, current: PythonInterpreter?) {
        // PythonInterpreterListener fires on the application bus, so the cache of each open project hears it.
        if (module.project == project) module.getUserData(LOAD_KEY)?.invalidate()
      }
    })
    // ModuleListener fires in the write action that removes or unloads the module. The read action in probeOf() and
    // this write action exclude each other, so no probe of a removed module continues.
    project.messageBus.connect(disposable).subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        module.getUserData(LOAD_KEY)?.cancel()
      }
    })
  }

  private suspend fun probeOf(pyProject: PyProject): Deferred<PyProjectConfigurators> {
    // The automatic configuration walks every project on a scope of its own, and a project can close under it. A
    // disposed module is not probed: that would run the tool of each configurator for something that is gone. The
    // refusal is the cancellation that the walk handles.
    val module = pyProject.residesOnModule
    return readAction {
      if (module.isDisposed) null
      else LOAD_KEY.getValue(module).share(pyProject)
    } ?: throw AlreadyDisposedException("Module $module is disposed, its configurators are not probed")
  }

  companion object {
    /** The shared probe of a module, which the module keeps in its user data, so the probe ends with the module. */
    private val LOAD_KEY: NotNullLazyKey<ExpiringSharedLoad<PyProject, PyProjectConfigurators>, Module> =
      NotNullLazyKey.createLazyKey("PySdkConfiguratorsCache.load") { module ->
        ExpiringSharedLoad(module.project.service<PySdkConfiguratorsCache>().scope, TTL, load = ::probe)
      }

    private suspend fun probe(pyProject: PyProject): PyProjectConfigurators {
      val venvs = pyProject.findPythonVirtualEnvironments()
      return PyProjectConfigurators(venvs, PyProjectSdkConfigurationExtension.findAllSorted(pyProject, venvs))
    }

    /**
     * The cached answer for [pyProject], probing only if no running or recent probe exists.
     *
     * The callers that arrive together while a project opens share a single probe rather than each starting one.
     * The probe runs on the scope of the cache, so a cancelled caller stops to wait, and the probe continues for the
     * rest. A probe that fails is not kept, and the next caller starts a new one.
     */
    suspend fun get(pyProject: PyProject): PyProjectConfigurators =
      pyProject.residesOnModule.project.service<PySdkConfiguratorsCache>().probeOf(pyProject).await()

    /** Drops the answer for [pyProject], for a caller that has just changed what a probe finds (a tool install). */
    fun invalidate(pyProject: PyProject) {
      pyProject.residesOnModule.getUserData(LOAD_KEY)?.invalidate()
    }
  }
}
