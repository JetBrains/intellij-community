// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.openapi.components.Service
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactory
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactoryImpl

@Service(Service.Level.PROJECT)
internal class TestRunnerServiceFactory
  : PyModuleServiceFactory<TestRunnerService.ModuleService>
    by PyModuleServiceFactoryImpl(TestRunnerService::ModuleService)
