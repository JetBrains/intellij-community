// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import org.jetbrains.annotations.ApiStatus

@State(name = "Pandoc.Settings", storages = [Storage(value = "pandoc.xml", roamingType = RoamingType.PER_OS)])
class PandocSettings: SimplePersistentStateComponent<PandocSettings.State>(State()) {
  @ApiStatus.Internal
  class State: BaseState() {
    var pathToPandoc by string()
    var pathToImages by string()
  }

  var pathToPandoc
    get() = state.pathToPandoc
    set(value) { state.pathToPandoc = value }

  var pathToImages
    get() = state.pathToImages
    set(value) { state.pathToImages = value }

  companion object {
    @JvmStatic
    fun getInstance(project: Project? = null): PandocSettings {
      return currentOrDefaultProject(project).service()
    }
  }
}
