// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil

// Ported from MavenImportingTestCase.setupJdkForModule(s): assigns the internal test JDK to a module of the
// fixture's imported project. Needed by code-insight/importing tests that must resolve JDK types after import.

fun MavenImportingTestFixture.setupJdkForModules(vararg moduleNames: String) {
  for (each in moduleNames) {
    setupJdkForModule(each)
  }
}

fun MavenImportingTestFixture.setupJdkForModule(moduleName: String): Sdk {
  val sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk()
  WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance(project).addJdk(sdk, testRootDisposable) }
  ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk)
  return sdk
}
