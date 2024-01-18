// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

@Service(Service.Level.PROJECT)
@State(name = "PoetryConfigService", storages = [Storage("poetry.xml")])
class PoetryConfigService : PersistentStateComponent<PoetryConfigService> {
  var poetryVirtualenvPaths = mutableSetOf<String>()

  override fun getState(): PoetryConfigService {
    return this
  }

  override fun loadState(config: PoetryConfigService) {
    XmlSerializerUtil.copyBean(config, this)
  }

  companion object {
    fun getInstance(project: Project): PoetryConfigService {
      return project.getService(PoetryConfigService::class.java)
    }
  }

}