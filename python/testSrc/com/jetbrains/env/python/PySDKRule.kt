// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.tools.SdkCreationRequest.LocalPython
import com.jetbrains.python.tools.SdkCreationRequest.RemotePython
import com.jetbrains.python.tools.createSdk
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource


/**
 * Creates python SDK either local (if [targetConfigProducer] is null) or target.
 * In case of target, it should have [com.jetbrains.python.tools.PYTHON_PATH_ON_TARGET]
 * Locals are search automatically like in [com.intellij.python.community.testFramework.testEnv.PyEnvTestSettings] or using [com.jetbrains.python.sdk.flavors.PythonSdkFlavor.suggestLocalHomePaths]
 */
class PySDKRule(private val targetConfigProducer: (() -> TargetEnvironmentConfiguration)?) : ExternalResource() {

  @Volatile
  lateinit var sdk: Sdk
    private set

  private lateinit var autoClosable: AutoCloseable

  override fun before() {
    val (sdk, autoClosable) = runBlocking { createSdk(targetConfigProducer?.let { RemotePython(it()) } ?: LocalPython()) }
    this.sdk = sdk
    this.autoClosable = autoClosable
  }

  override fun after() {
    autoClosable.close()
  }
}
