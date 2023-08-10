// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "MarkdownSettingsMigration", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
internal class MarkdownSettingsMigration: SimplePersistentStateComponent<MarkdownSettingsMigration.State>(State()) {
  class State: BaseState() {
    var stateVersion by property(0)
  }

  companion object {
    fun getInstance(project: Project): MarkdownSettingsMigration {
      return project.service()
    }
  }
}
