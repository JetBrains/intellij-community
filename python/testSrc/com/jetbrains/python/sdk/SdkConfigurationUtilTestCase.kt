// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualFile
import com.jetbrains.python.junit5.env.PyEnvTestCase
import com.jetbrains.python.junit5.env.PythonBinaryPath
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

@PyEnvTestCase
class SdkConfigurationUtilTestCase {

  @Test
  fun setupSdk(@PythonBinaryPath sdkPath: Path) {
    val sdk = SdkConfigurationUtil.setupSdk(emptyArray(), sdkPath.refreshAndGetVirtualFile(), PythonSdkType.getInstance(), null, null)
    assertNotNull(sdk.versionString, "$sdk must have version")
    assertNotNull(sdk.name, "$sdk must have name")
  }
}