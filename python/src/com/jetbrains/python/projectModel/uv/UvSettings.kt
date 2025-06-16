// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.jetbrains.python.projectModel.ProjectModelSettings
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath

// TODO SerializablePersistentStateComponent
@Service(Service.Level.PROJECT)
@State(name = "UvSettings", storages = [Storage("uv.xml")])
class UvSettings(private val project: Project) :
  SimplePersistentStateComponent<UvSettings.State>(State()), ProjectModelSettings {

  class State() : BaseState() {
    var linkedProjects: MutableList<String> by list()
  }

  override fun setLinkedProjects(projects: List<Path>) {
    val oldLinkedProjects = getLinkedProjects()
    val removedLinkedProjects = oldLinkedProjects - projects
    val addedLinkedProjects = projects - oldLinkedProjects
    val listener = project.messageBus.syncPublisher(UvSettingsListener.Companion.TOPIC)
    removedLinkedProjects.forEach { listener.onLinkedProjectRemoved(it) }
    addedLinkedProjects.forEach { listener.onLinkedProjectAdded(it) }

    state.linkedProjects = projects.map { it.toUri().toString() }.toMutableList()
  }

  override fun getLinkedProjects(): List<Path> {
    return state.linkedProjects.map { URI(it).toPath() }
  }

  override fun addLinkedProject(projectRoot: Path) {
    val existing = getLinkedProjects()
    if (projectRoot !in existing) {
      setLinkedProjects(existing + listOf(projectRoot))
    }
  }

  override fun removeLinkedProject(projectRoot: Path) {
    val existing = getLinkedProjects()
    if (projectRoot in existing) {
      setLinkedProjects(existing - listOf(projectRoot))
    }
  }
}