// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPathOrNull
import com.intellij.platform.eel.provider.getEelDescriptor
import java.nio.file.Path

/**
 * This class contains a per-project storage of maven home directory for maven macros.
 * This location cannot be defined globally, as we might have several projects belonging to different environments (e.g., different docker containers)
 */
@Service(Service.Level.PROJECT)
@State(
  name = "MavenRepositoryManager",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE, usePathMacroManager = false)],
)
class MavenSerializedRepositoryManager(private val project: Project) : PersistentStateComponent<MavenSerializedRepositoryManager.State> {
  private var myState: State = State()
  private var path: Path? = null

  override fun getState(): MavenSerializedRepositoryManager.State {
    return myState
  }

  override fun loadState(state: MavenSerializedRepositoryManager.State) {
    myState.mavenHomePath = state.mavenHomePath
    path = myState.mavenHomePath?.let {
      val descriptor = project.getEelDescriptor()
      EelPath.parse(it, descriptor).asNioPathOrNull()
    }
  }

  class State : BaseState() {
    // This path is local to a machine, which means that it goes _without_ the routing prefix.
    var mavenHomePath: String? by string()
  }

  fun isOverriden(): Boolean {
    return path == null
  }

  fun getMavenHomePath(): Path {
    val currentPath = path
    if (currentPath != null) {
      return currentPath
    }
    val projectFilePath = requireNotNull(project.projectFilePath) { "Components should not be loaded for a default project" }
    val detectedHomePath = MavenUtil.resolveDefaultLocalRepository(Path.of(projectFilePath))
    val detectedHomePathString = detectedHomePath.asEelPath().toString()
    myState.mavenHomePath = detectedHomePathString
    path = detectedHomePath
    return detectedHomePath
  }
}

