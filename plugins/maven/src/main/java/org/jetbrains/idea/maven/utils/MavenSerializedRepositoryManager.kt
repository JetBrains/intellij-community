// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.impl.utils.getEelApi
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * This class contains a per-project storage of maven home directory.
 * This location cannot be defined globally, as we might have several projects belonging to different environments (e.g., different docker containers)
 */
@Suppress("RAW_RUN_BLOCKING")
@Service(Service.Level.PROJECT)
@State(
  name = "MavenRepositoryManager",
  storages = [Storage(MavenSerializedRepositoryManager.MAVEN_REPOSITORY_MANAGER_STORAGE, usePathMacroManager = false)],
)
class MavenSerializedRepositoryManager(private val project: Project) : PersistentStateComponent<MavenSerializedRepositoryManager.State> {
  companion object {
    @ApiStatus.Internal
    const val MAVEN_REPOSITORY_MANAGER_STORAGE: String = "mavenHomeManager.xml"
  }

  private var myState: State = State()
  private var path: Path? = null

  override fun getState(): MavenSerializedRepositoryManager.State {
    return myState
  }

  override fun loadState(state: MavenSerializedRepositoryManager.State) {
    myState.mavenHomePath = state.mavenHomePath
    path = myState.mavenHomePath?.let { runBlocking { project.getEelApi() }.mapper.toNioPath(EelPath.parse(it, null)) }
  }

  class State : BaseState() {
    // This path is local to a machine, which means that it goes _without_ the routing prefix.
    var mavenHomePath: String? by string()
  }

  fun getMavenHomePath(): Path {
    val currentPath = path
    if (currentPath != null) {
      return currentPath
    }
    val projectFilePath = requireNotNull(project.projectFilePath) { "Components should not be loaded for a default project" }
    val detectedHomePath = MavenUtil.resolveDefaultLocalRepository(Path.of(projectFilePath))
    val detectedHomePathString = runBlocking { project.getEelApi() }.mapper.getOriginalPath(detectedHomePath)?.toString()
    myState.mavenHomePath = detectedHomePathString
    path = detectedHomePath
    return detectedHomePath
  }
}

