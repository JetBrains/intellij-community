// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.junit5Tests.framework.env.SdkFixture
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.uv.getOrDownloadUvExecutable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil
import com.jetbrains.python.tools.createUvPipVenvSdk
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

/**
 * Create virtual env using UV and setting uv as a Package manager via ui pip. If [addToSdkTable] then also added to the project jdk table.
 *
 * Similar to [pyVenvFixture] but uses `uv venv` instead of virtualenv helper.
 * UV is significantly faster for venv creation and package installation.
 * UV will be downloaded automatically if not present.
 *
 */
fun TestFixture<SdkFixture<PyEnvironment>>.pyUvVenvFixture(
  addToSdkTable: Boolean,
  moduleFixture: TestFixture<Module>,
): TestFixture<Sdk> = testFixture {
  val env = this@pyUvVenvFixture.init().env
  val module = moduleFixture.init()
  val baseDirPath = module.baseDir?.toNioPath()
  requireNotNull(baseDirPath) { "Module $module has no base dir" }
  val venvDir = baseDirPath.resolve(".venv")


  // Get or download UV executable
  val uvExecutable = getOrDownloadUvExecutable(LATEST_UV_VERSION)

  // Create venv using UV
  runExecutableWithProgress(
    uvExecutable, venvDir.parent, 10.minutes, emptyMap(),
    "venv", venvDir.pathString, "--python", env.pythonPath.pathString
  ).getOrThrow()


  // Find Python in created venv
  val venvPython = withContext(Dispatchers.IO) {
    VirtualEnvReader().findPythonInPythonRoot(venvDir)
  } ?: error("Python executable not found in UV venv: $venvDir")

  val venvSdk = withContext(Dispatchers.IO) { createUvPipVenvSdk(venvPython, baseDirPath) }

  if (addToSdkTable) {
    venvSdk.persist()
    module.pythonSdk = venvSdk
    venvSdk.setAssociationToModule(module)
  }
  // workaround interesting behavior of VFS_STRUCTURAL_MODIFICATIONS
  PySkeletonUtil.getSitePackagesDirectory(venvSdk)?.getChildren()

  initialized(venvSdk) {
    edtWriteAction {
      ProjectJdkTable.getInstance().removeJdk(venvSdk)
    }
  }
}
