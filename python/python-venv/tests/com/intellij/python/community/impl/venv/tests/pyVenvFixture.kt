// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.venv.tests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3
import com.intellij.python.junit5Tests.framework.env.SdkFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path


/**
 * Create virtual env in [where]. If [addToSdkTable] then also added to the project jdk table
 */
fun TestFixture<SdkFixture<PythonBinary>>.pyVenvFixture(
  where: TestFixture<Path>,
  addToSdkTable: Boolean,
  moduleFixture: TestFixture<Module>? = null,
): TestFixture<Sdk> = testFixture {
  val basePython = this@pyVenvFixture.init().env
  withContext(Dispatchers.EDT) {
    val module = moduleFixture?.init()
    val venvDir = where.init()
    val venvPython = createVenv(basePython, venvDir).getOrThrow()
    val venvSdk = withContext(Dispatchers.IO){ TypeVanillaPython3.createSdk(venvPython)}
    if (addToSdkTable) {
      venvSdk.persist()
      if (module != null) {
        ModuleRootModificationUtil.setModuleSdk(module, venvSdk)
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

