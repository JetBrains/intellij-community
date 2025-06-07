// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.jetbrains.python.projectModel.BaseProjectModelService
import com.jetbrains.python.projectModel.ProjectModelSettings
import com.jetbrains.python.projectModel.ProjectModelSyncListener
import com.jetbrains.python.projectModel.PythonProjectModelResolver
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Syncs the project model described in pyproject.toml files with the IntelliJ project model.
 */
object UvProjectModelService : BaseProjectModelService<UvEntitySource>() {
  override val projectModelResolver: PythonProjectModelResolver
    get() = UvProjectModelResolver

  override val systemName: @NlsSafe String
    get() = "Uv"

  override fun getSettings(project: Project): ProjectModelSettings = project.service<UvSettings>()

  override fun getSyncListener(project: Project): ProjectModelSyncListener = project.messageBus.syncPublisher(UvSyncListener.TOPIC)

  override fun createEntitySource(project: Project, singleProjectRoot: Path): EntitySource {
    val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    return UvEntitySource(singleProjectRoot.toVirtualFileUrl(fileUrlManager))
  }

  override fun getEntitySourceClass(): KClass<out EntitySource> = UvEntitySource::class
}
