// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@State(name = "Pandoc.Settings", storages = [Storage("pandoc.xml")], allowLoadInTests = true)
internal class PandocSettings: SimplePersistentStateComponent<PandocSettings.State>(State()) {
  @ApiStatus.Internal
  class State: BaseState() {
    /** The legacy executable path retained for migration to application settings. */
    @Deprecated("Use PandocApplicationSettings.pathToPandoc. This field is retained for migration.")
    var pathToPandoc: String? by string()
    var pathToImages: String? by string()
  }

  var pathToImages: String?
    get() = state.pathToImages
    set(value) { state.pathToImages = value }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PandocSettings = project.service()
  }
}
