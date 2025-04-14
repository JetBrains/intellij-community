// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath

// TODO SerializablePersistentStateComponent
@Service(Service.Level.PROJECT)
@State(name = "PoetrySettings", storages = [Storage("poetry.xml")])
class PoetrySettings(private val project: Project) : SimplePersistentStateComponent<PoetrySettings.State>(State()) {

  class State() : BaseState() {
    var linkedProjects: MutableList<String> by list()
  }

  fun setLinkedProjects(projects: List<Path>) {
    val oldLinkedProjects = getLinkedProjects()
    val removedLinkedProjects = oldLinkedProjects - projects
    val addedLinkedProjects = projects - oldLinkedProjects
    val listener = project.messageBus.syncPublisher(PoetrySettingsListener.Companion.TOPIC)
    removedLinkedProjects.forEach { listener.onLinkedProjectRemoved(it) }
    addedLinkedProjects.forEach { listener.onLinkedProjectAdded(it) }

    state.linkedProjects = projects.map { it.toUri().toString() }.toMutableList()
  }

  fun getLinkedProjects(): List<Path> {
    return state.linkedProjects.map { URI(it).toPath() }
  }

  fun addLinkedProject(projectRoot: Path) {
    val existing = getLinkedProjects()
    if (projectRoot !in existing) {
      setLinkedProjects(existing + listOf(projectRoot))
    }
  }

  fun removeLinkedProject(projectRoot: Path) {
    val existing = getLinkedProjects()
    if (projectRoot in existing) {
      setLinkedProjects(existing - listOf(projectRoot))
    }
  }
}