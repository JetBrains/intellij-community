package com.jetbrains.python.documentation

import com.intellij.openapi.components.Service
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactory
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactoryImpl

@Service(Service.Level.PROJECT)
internal class PyDocumentationSettingsFactory
  : PyModuleServiceFactory<PyDocumentationSettings.ModuleService>
    by PyModuleServiceFactoryImpl(PyDocumentationSettings::ModuleService)
