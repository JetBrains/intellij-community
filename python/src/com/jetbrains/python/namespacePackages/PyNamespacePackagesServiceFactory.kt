// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.namespacePackages

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactory
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactoryImpl

@Service(Service.Level.PROJECT)
internal class PyNamespacePackagesServiceFactory
  : PyModuleServiceFactory<PyNamespacePackagesService> by PyModuleServiceFactoryImpl(::PyNamespacePackagesService) {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyNamespacePackagesServiceFactory {
      return project.service<PyNamespacePackagesServiceFactory>()
    }
  }
}
