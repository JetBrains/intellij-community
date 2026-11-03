// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The watchers of the paths of the Python environments of the application, see [PythonEnvironmentKey]: one file
 * listener for each environment, for every project that watches it. A change of the children of a directory of the
 * `sys.path` of an environment, such as a package that is installed there, runs the runnable of each project that
 * watches it, such as a request for an update of its interpreter. The updates of one environment merge into one, see
 * [PythonEnvironments].
 *
 * The paths are the `sys.path` of the last update of the environment, see [PythonEnvironments.factsIfReady]. An
 * environment that no update read yet has no paths, and its first update gives them.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class PythonEnvironmentWatchers {
  private class Environment(val listener: Disposable) {
    val watches = CopyOnWriteArrayList<Runnable>()
  }

  private val environments = ConcurrentHashMap<PythonEnvironmentKey, Environment>()

  /**
   * Runs [onChange] on a pooled thread on each change under the paths of the environment [key], while [parent] lives. A
   * [parent] that is already disposed watches nothing.
   */
  fun watch(key: PythonEnvironmentKey, parent: Disposable, onChange: Runnable) {
    synchronized(environments) {
      if (!Disposer.tryRegister(parent) { unwatch(key, onChange) }) return
      environments.computeIfAbsent(key) { Environment(listen(it)) }.watches += onChange
    }
  }

  private fun unwatch(key: PythonEnvironmentKey, onChange: Runnable) {
    synchronized(environments) {
      val environment = environments[key] ?: return
      environment.watches -= onChange
      if (environment.watches.isEmpty()) {
        environments.remove(key)
        Disposer.dispose(environment.listener)
      }
    }
  }

  private fun listen(key: PythonEnvironmentKey): Disposable {
    val listener = Disposer.newDisposable("The paths of the Python environment $key")
    VirtualFileManager.getInstance().addAsyncFileListener(object : AsyncFileListener {
      override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val roots = rootsOf(key)
        if (roots.isEmpty() || events.none { it.changesChildrenOf(roots) }) return null
        environments[key]?.watches?.forEach { ApplicationManager.getApplication().executeOnPooledThread(it) }
        // No continuation in a write action is needed.
        return null
      }
    }, listener)
    return listener
  }

  /** The directories of the `sys.path` of [key], as the last update read it. */
  private fun rootsOf(key: PythonEnvironmentKey): Set<VirtualFile> =
    PythonEnvironments.getInstance().factsIfReady(key)?.sysPath.orEmpty().mapNotNullTo(HashSet()) { path ->
      try {
        LocalFileSystem.getInstance().findFileByNioFile(Path.of(path))
      }
      catch (_: InvalidPathException) {
        null
      }
    }

  /** The number of the file listeners, one for each watched environment. */
  @TestOnly
  fun listenerCountForTests(): Int = environments.size

  companion object {
    @JvmStatic
    fun getInstance(): PythonEnvironmentWatchers = service()
  }
}

/** Whether this event changes the children of one of [roots]. A change of the content or of a property does not. */
private fun VFileEvent.changesChildrenOf(roots: Set<VirtualFile>): Boolean {
  if (this is VFileContentChangeEvent || this is VFilePropertyChangeEvent) return false
  // A create event has no file yet, only its parent.
  val parent = if (this is VFileCreateEvent) parent else file?.parent
  return parent != null && parent in roots
}
