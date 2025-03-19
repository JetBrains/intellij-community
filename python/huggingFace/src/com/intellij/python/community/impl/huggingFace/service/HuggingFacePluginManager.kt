// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCacheUpdateHandler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFacePluginManager(val project: Project) : Disposable {
  private var libraryStatusChecker: HuggingFaceLibrariesTracker = project.getService(HuggingFaceLibrariesTracker::class.java)
  init { project.getService(HuggingFaceCacheUpdateHandler::class.java) }
  fun isActive(): Boolean = libraryStatusChecker.isAnyHFLibraryInstalled() && Registry.`is`("python.enable.hugging.face.cards")
  override fun dispose() = Disposer.dispose(libraryStatusChecker)
}
