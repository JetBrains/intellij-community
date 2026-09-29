// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.PySdkListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
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
  init {
    // An SDK appearing is the one event that makes every answer here obsolete at once: with an SDK the
    // project needs no configuring, and until then the options are what the probes found. PythonPluginDisposable
    // parents the connection so it goes away on plugin unload.
    ApplicationManager.getApplication().messageBus.connect(PythonPluginDisposable.getInstance(project)).subscribe(PySdkListener.TOPIC, object : PySdkListener {
      override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) {
        // PySdkListener fires on the application bus, so this is told about every open project's modules.
        // Dropping another project's entry would be someone else's cache to clear (cf. PY-91324).
        if (module.project == project) invalidate(module)
      }
    })
  }

  // Keyed by the module a project resides on, and not by the project itself: the module is the [Disposable] the entry
  // is registered on, and the one identity `PySdkListener` reports an SDK change for.
  private val cache: AsyncCache<Module, PyProjectConfigurators> = Caffeine.newBuilder()
    .expireAfterWrite(TTL.inWholeSeconds, TimeUnit.SECONDS)
    .buildAsync()

  /**
   * The cached answer for [pyProject], probing only if no live or recent one exists.
   *
   * `AsyncCache.get` runs the mapping at most once per key, so the callers that arrive together while a
   * project opens share a single probe rather than each starting one. The load runs on this service's
   * scope, so whichever caller happened to trigger it can be cancelled without killing it for the rest.
   */
  suspend fun get(pyProject: PyProject): PyProjectConfigurators =
    cache.get(pyProject.residesOnModule) { key, _ ->
      // Break the strong reference chain from a cached value to a disposed module: the options hold
      // creator closures that capture the project (and WillInstallTool holds a pathPersister that does
      // too), and a project holds its module, which would otherwise keep a closed project alive until
      // the TTL ran out.
      //
      // Registering is also how a disposed module is recognized, in one step that cannot race: the automatic
      // configuration walks every project on a scope of its own and a project can close under it, so a caller can
      // arrive with a module that has just gone. Such a module is not probed — that would run every configurator's
      // tool for something that no longer exists — and the refusal is the cancellation the rest of that walk already
      // handles, rather than the IncorrectOperationException plain `register` raises (PY-91964).
      @Suppress("IncorrectParentDisposable")
      if (!Disposer.tryRegister(key) { invalidate(key) }) {
        throw AlreadyDisposedException("Module $key is disposed, its configurators are not probed")
      }
      scope.future { probe(pyProject) }
    }.await()

  /** Drops [module]'s entry, for a caller that has just changed what a probe would find (installing a tool). */
  fun invalidate(module: Module) {
    cache.synchronous().invalidate(module)
  }

  private suspend fun probe(pyProject: PyProject): PyProjectConfigurators {
    val venvs = pyProject.findPythonVirtualEnvironments()
    return PyProjectConfigurators(venvs, PyProjectSdkConfigurationExtension.findAllSorted(pyProject, venvs))
  }

  companion object {
    fun getInstance(project: Project): PySdkConfiguratorsCache = project.service()
  }
}
