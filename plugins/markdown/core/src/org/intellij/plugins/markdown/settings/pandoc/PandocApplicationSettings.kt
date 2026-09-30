// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@Service(Service.Level.APP)
@State(name = "Pandoc.Settings", storages = [Storage(value = "pandoc.xml", roamingType = RoamingType.PER_OS)])
internal class PandocApplicationSettings : SimplePersistentStateComponent<PandocApplicationSettings.State>(State()) {
  class State : BaseState() {
    var pathToPandoc: String? by string()
  }

  var pathToPandoc: String?
    get() = state.pathToPandoc
    set(value) { state.pathToPandoc = value }

  override fun noStateLoaded() {
    loadState(State())
  }

  /** Adopts an eligible project executable if the application path is empty. */
  suspend fun reconcileWithProject(project: Project) {
    if (!state.pathToPandoc.isNullOrEmpty()) return
    if (project.isDefault || !TrustedProjects.isProjectTrusted(project)) return
    @Suppress("DEPRECATION")
    val path = project.serviceAsync<PandocSettings>().state.pathToPandoc?.takeIf { it.isNotBlank() } ?: return
    val roots = readAction {
      val contentRoots = ProjectRootManager.getInstance(project).contentRoots.map { it.toNioPath() }
      project.basePath?.let { contentRoots + listOf(Path.of(it)) } ?: contentRoots
    }
    val outsideProject = withContext(Dispatchers.IO) {
      try {
        val executable = Path.of(path)
        if (!executable.isAbsolute || !executable.isRegularFile() || roots.isEmpty()) return@withContext false
        val normalized = executable.normalize()
        val resolved = executable.toRealPath()
        roots.none { root ->
          normalized.startsWith(root.toAbsolutePath().normalize()) || resolved.startsWith(root.toRealPath())
        }
      }
      catch (_: InvalidPathException) {
        false
      }
      catch (_: IOException) {
        false
      }
    }
    if (!outsideProject) return
    withContext(Dispatchers.EDT) {
      if (state.pathToPandoc.isNullOrEmpty() && TrustedProjects.isProjectTrusted(project)) {
        pathToPandoc = path
      }
    }
  }

  fun resolveExecutable(): String {
    return pathToPandoc ?: PandocExecutableDetector.detect()
  }

  companion object {
    fun getInstance(): PandocApplicationSettings = service()
  }
}
