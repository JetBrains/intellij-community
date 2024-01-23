// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "IcsProjectId", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))], reportStatistic = false)
class ProjectId : PersistentStateComponent<ProjectId> {
  var uid: String? = null
  var path: String? = null

  override fun getState(): ProjectId {
    return this
  }

  override fun loadState(state: ProjectId) {
    XmlSerializerUtil.copyBean(state, this)
  }
}