// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.junit5Tests.framework.env.SdkFixture
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.venv.createVenv
import com.intellij.python.venv.createVenvAdditionalData
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.SdkCreationAdvancedOpts
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Create virtual env in [where]. If [addToSdkTable] then also added to the project jdk table
 */
fun TestFixture<SdkFixture<PyEnvironment>>.pyVenvFixture(
  where: TestFixture<Path>,
  addToSdkTable: Boolean,
  moduleFixture: TestFixture<Module>? = null,
): TestFixture<Sdk> = testFixture {
  val env = this@pyVenvFixture.init().env
  withContext(Dispatchers.EDT) {
    val module = moduleFixture?.init()
    val workingDirectory = where.init()
    val venvDir = workingDirectory.resolve(".venv")
    val venvPython = createVenv(env.pythonPath, venvDir).getOrThrow()
    val additionalData = createVenvAdditionalData(workingDirectory)
    if (module == null) {
      // With no module this fixture stands for a *shared* venv, so it must not keep the association a new SDK derives
      // from its working directory: sortForExistingEnvironment only treats an unassociated SDK as SHARED_VENVS.
      additionalData.associatedModulePath = null
    }
    val venvSdk = createSdk(
      PathHolder.Eel(venvPython),
      additionalData,
      advancedOpts = SdkCreationAdvancedOpts(persist = addToSdkTable),
    ).orThrow()
    if (addToSdkTable) {
      if (module != null) {
        module.pythonSdk = venvSdk
        venvSdk.setAssociationToModule(module)
      }
    }
    initialized(venvSdk) {
      edtWriteAction {
        ProjectJdkTable.getInstance().removeJdk(venvSdk)
      }
    }
  }
}