// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PreviewCommandRunnability {
  fun isRunnable(project: Project, file: VirtualFile?, command: String, allowRunConfigurations: Boolean): Boolean

  fun execute(
    project: Project,
    file: VirtualFile?,
    command: String,
    executorId: String,
    workingDirectory: String,
  )

  suspend fun resolvePending(): Boolean

  companion object {
    fun getInstance(): PreviewCommandRunnability = serviceOrNull() ?: DummyPreviewCommandRunnability
  }
}

private object DummyPreviewCommandRunnability : PreviewCommandRunnability {
  override fun isRunnable(project: Project, file: VirtualFile?, command: String, allowRunConfigurations: Boolean): Boolean = false
  override fun execute(project: Project, file: VirtualFile?, command: String, executorId: String, workingDirectory: String) {}
  override suspend fun resolvePending(): Boolean = false
}
