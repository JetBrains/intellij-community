// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.python.test.env.common.createEnvironment
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.RunOnEnvironmentsExtension
import com.intellij.python.test.env.junit5.getOrCreatePyEnvironmentFactory
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.sdk.persist

/**
 * Create sdk fixture using tags from [@PyEnvTestCase][com.intellij.python.junit5Tests.framework.env.PyEnvTestCase] annotation.
 * Requires test class to be annotated with @PyEnvTestCase.
 *
 * @throws IllegalStateException if @PyEnvTestCase annotation is not found on the test class
 */
fun pySdkFixture(): TestFixture<SdkFixture<PyEnvironment>> = testFixture { context ->
  initializedTestFixture(RunOnEnvironmentsExtension.getPythonEnvironment(context.extensionContext))
}

/**
 * Creates [Sdk] (if you only need a python path, use [com.intellij.python.junit5Tests.framework.env.PythonBinaryPath]
 * or [com.intellij.python.community.junit5Tests.framework.conda.CondaEnv])
 */
fun pySdkFixture(
  env: PredefinedPyEnvironments,
): TestFixture<SdkFixture<PyEnvironment>> = testFixture { context ->
  val factory = getOrCreatePyEnvironmentFactory(context.extensionContext)
  initializedPySdkFixture(factory, env)
}

private suspend fun TestFixtureInitializer.R<SdkFixture<PyEnvironment>>.initializedPySdkFixture(
  factory: PyEnvironmentFactory,
  env: PredefinedPyEnvironments,
): TestFixtureInitializer.InitializedTestFixture<SdkFixture<PyEnvironment>> {
  val env = factory.createEnvironment(env)
  return initializedTestFixture(env)
}

private suspend fun TestFixtureInitializer.R<SdkFixture<PyEnvironment>>.initializedTestFixture(
  env: PyEnvironment,
): TestFixtureInitializer.InitializedTestFixture<SdkFixture<PyEnvironment>> {
  val sdk = env.prepareSdk()
  sdk.persist()
  return initialized(SdkFixture(sdk, env)) {
    edtWriteAction {
      ProjectJdkTable.getInstance().removeJdk(sdk)
      env.close()
    }
  }
}