/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.settingsRepository

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "IcsProjectId", storages = arrayOf(Storage(StoragePathMacros.WORKSPACE_FILE)))
class ProjectId : PersistentStateComponent<ProjectId> {
  var uid: String? = null
  var path: String? = null

  override fun getState(): ProjectId? {
    return this
  }

  override fun loadState(state: ProjectId) {
    XmlSerializerUtil.copyBean(state, this)
  }
}