// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
internal class StringDescriptorManagerFactory(
  project: Project,
) {
  private val instances: ConcurrentMap<Module, StringDescriptorManager> = ConcurrentHashMap()

  init {
    project.getMessageBus().connect().subscribe<ModuleRootListener>(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        instances.forEach { (_, manager) ->
          manager.clear()
        }
      }
    })
  }

  fun getService(module: Module): StringDescriptorManager {
    return instances.computeIfAbsent(module) {
      Disposer.register(module) {
        instances.remove(module)
      }
      StringDescriptorManager(module)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): StringDescriptorManagerFactory {
      return project.service<StringDescriptorManagerFactory>()
    }
  }
}
