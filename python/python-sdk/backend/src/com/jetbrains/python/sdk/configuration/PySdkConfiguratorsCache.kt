// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.backend.getOrLoad
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.serviceContainer.AlreadyDisposedException
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.PythonInterpreterListener
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
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
 * Callers that need an answer which is true *right now* must not come here — see
 * [PyProjectSdkConfigurationExtension.findAllSorted], which stays uncached for exactly that.
 */
@Service(Service.Level.PROJECT)
internal class PySdkConfiguratorsCache(private val project: Project, private val scope: CoroutineScope) {
  // Keyed by the module that a project resides on: the one identity that `PythonInterpreterListener` and
  // `ModuleListener` report a change for. Declared above `init`, because the listeners of `init` read it.
  private val cache: AsyncCache<Module, PyProjectConfigurators> = Caffeine.newBuilder()
    .expireAfterWrite(TTL.inWholeSeconds, TimeUnit.SECONDS)
    .buildAsync()

  init {
    val disposable = PythonPluginDisposable.getInstance(project)
    // An SDK appearing is the one event that makes every answer here obsolete at once: with an SDK the
    // project needs no configuring, and until then the options are what the probes found.
    val applicationBus = ApplicationManager.getApplication().messageBus
    applicationBus.connect(disposable).subscribe(PythonInterpreterListener.TOPIC, object : PythonInterpreterListener {
      override fun moduleInterpreterChanged(module: Module, previous: PythonInterpreter?, current: PythonInterpreter?) {
        // PythonInterpreterListener fires on the application bus, so the cache of each open project hears it.
        if (module.project == project) cancel(module)
      }
    })
    // ModuleListener fires in the write action that removes or unloads the module, before the module is disposed.
    project.messageBus.connect(disposable).subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        cancel(module)
      }
    })
  }

  private suspend fun load(pyProject: PyProject): PyProjectConfigurators =
    cache.getOrLoad(pyProject.residesOnModule, scope) { module ->
      // The automatic configuration walks every project on a scope of its own, and a project can close under it. A
      // disposed module is not probed: that would run the tool of each configurator for something that is gone. The
      // read action and the write action of moduleRemoved() exclude each other. So either moduleRemoved() cancels
      // this load, or the check sees the disposed module.
      if (readAction { module.isDisposed }) {
        throw AlreadyDisposedException("Module $module is disposed, its configurators are not probed")
      }
      probe(pyProject)
    }

  /**
   * Removes the entry of [module] and cancels its future. The cancel of a `scope.future {}` future also cancels its
   * coroutine, so the tool processes of the probe stop.
   */
  private fun cancel(module: Module) {
    cache.asMap().remove(module)?.cancel(true)
  }

  private suspend fun probe(pyProject: PyProject): PyProjectConfigurators {
    val venvs = pyProject.findPythonVirtualEnvironments()
    return PyProjectConfigurators(venvs, PyProjectSdkConfigurationExtension.findAllSorted(pyProject, venvs))
  }

  companion object {
    /**
     * The cached answer for [pyProject], probing only if no running or recent probe exists.
     *
     * The callers that arrive together while a project opens share a single probe rather than each starting one.
     * The probe runs on the scope of the cache, so a cancelled caller stops to wait, and the probe continues for the
     * rest (see [getOrLoad]).
     *
     * A module that is disposed before the call gives [AlreadyDisposedException]. A removal of the module or an
     * [invalidate] during the probe gives a plain [kotlin.coroutines.cancellation.CancellationException]. Both are
     * cancellations, and a caller must not tell them apart by the type.
     */
    suspend fun get(pyProject: PyProject): PyProjectConfigurators =
      pyProject.project.service<PySdkConfiguratorsCache>().load(pyProject)

    /**
     * Drops the answer for [pyProject], for a caller that has just changed what a probe finds (a tool install). A
     * running probe is cancelled, so its tool processes stop.
     */
    fun invalidate(pyProject: PyProject) {
      pyProject.project.service<PySdkConfiguratorsCache>().cancel(pyProject.residesOnModule)
    }
  }
}
