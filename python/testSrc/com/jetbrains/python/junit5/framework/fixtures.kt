// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

@TestOnly
fun TestFixture<Project>.pyMockSdkFixture(module: TestFixture<Module>, sdkProvider: () -> Sdk):
  TestFixture<Sdk> = testFixture {
  this@pyMockSdkFixture.init()
  module.init()
  val sdk = sdkProvider()
  writeAction {
    ProjectJdkTable.getInstance().addJdk(sdk)
    ModuleRootModificationUtil.setModuleSdk(module.get(), sdk)
  }
  initialized(sdk) {
    writeAction {
      ModuleRootModificationUtil.setModuleSdk(module.get(), null)
      ProjectJdkTable.getInstance().removeJdk(sdk)
    }
  }
}