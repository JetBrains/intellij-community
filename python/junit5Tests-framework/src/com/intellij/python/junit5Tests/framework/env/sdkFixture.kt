// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.testFramework.testEnv.PythonType
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.persist
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Sdk with environment info. Use it as a regular sdk, but env fixtures (venv, conda) might use [env]
 */
@Internal
class SdkFixture<ENV : Any> internal constructor(private val sdk: Sdk, val env: ENV) : Sdk by sdk


/**
 * Create sdk fixture with [TypeVanillaPython3]
 */
fun pySdkFixture(): TestFixture<SdkFixture<PythonBinary>> = pySdkFixture(TypeVanillaPython3)

/**
 * Creates [Sdk] (if you only need a python path, use [PythonBinaryPath] or [com.intellij.python.community.junit5Tests.framework.conda.CondaEnv])
 */
fun <ENV : Any> pySdkFixture(
  pythonType: PythonType<ENV>,
): TestFixture<SdkFixture<ENV>> = testFixture {
  val (sdk, autoClosable, env) = pythonType.createSdkClosableEnv().getOrThrow()
  sdk.persist()
  initialized(SdkFixture<ENV>(sdk, env)) {
    edtWriteAction {
      ProjectJdkTable.getInstance().removeJdk(sdk)
      autoClosable.close()
    }
  }
}
