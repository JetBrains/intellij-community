package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

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
      return ServiceManager.getService(project, PoetryConfigService::class.java)
    }
  }

}