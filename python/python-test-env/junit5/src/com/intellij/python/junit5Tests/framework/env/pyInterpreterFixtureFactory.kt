// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.python.test.env.junit5.RunOnEnvironmentsExtension
import com.intellij.python.test.env.junit5.getOrCreatePyEnvironmentFactory
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter

/**
 * Creates an interpreter fixture using tags from [@PyEnvTestCase][com.intellij.python.junit5Tests.framework.env.PyEnvTestCase] annotation.
 * Requires test class to be annotated with @PyEnvTestCase. The interpreter belongs to this [PyProject].
 *
 * @throws IllegalStateException if @PyEnvTestCase annotation is not found on the test class
 */
@JvmName("pyProjectInterpreterFixture")
fun TestFixture<PyProject>.pyInterpreterFixture(): TestFixture<PyInterpreterFixture<PyEnvironment>> = testFixture { context ->
  val pyProject = this@pyInterpreterFixture.init()
  initializedTestFixture(pyProject, RunOnEnvironmentsExtension.getPythonEnvironment(context.extensionContext))
}

/**
 * Creates an interpreter (if you only need a python path, use [com.intellij.python.junit5Tests.framework.env.PythonBinaryPath]
 * or [com.intellij.python.community.junit5Tests.framework.conda.CondaEnv]). The interpreter belongs to this [PyProject].
 */
@JvmName("pyProjectInterpreterFixture")
fun TestFixture<PyProject>.pyInterpreterFixture(
  env: PredefinedPyEnvironments,
): TestFixture<PyInterpreterFixture<PyEnvironment>> = pyInterpreterFixture(env.spec)

@JvmName("pyProjectInterpreterFixture")
fun TestFixture<PyProject>.pyInterpreterFixture(
  envSpec: PyEnvironmentSpec<*>,
): TestFixture<PyInterpreterFixture<PyEnvironment>> = testFixture { context ->
  val pyProject = this@pyInterpreterFixture.init()
  val factory = getOrCreatePyEnvironmentFactory(context.extensionContext)
  initializedTestFixture(pyProject, factory.createEnvironment(envSpec))
}

/**
 * Creates the environment from the tags of [@PyEnvTestCase][com.intellij.python.junit5Tests.framework.env.PyEnvTestCase].
 * Requires the test class to be annotated with @PyEnvTestCase. The environment adds no interpreter to a project: use it
 * as the base Python of a venv fixture, which needs only its binary.
 */
fun pyEnvironmentFixture(): TestFixture<PyEnvironment> = testFixture { context ->
  val env = RunOnEnvironmentsExtension.getPythonEnvironment(context.extensionContext)
  initialized(env) {
    env.close()
  }
}

/** Creates the environment [env], see the overload without arguments. */
fun pyEnvironmentFixture(env: PredefinedPyEnvironments): TestFixture<PyEnvironment> = testFixture { context ->
  val environment = getOrCreatePyEnvironmentFactory(context.extensionContext).createEnvironment(env.spec)
  initialized(environment) {
    environment.close()
  }
}

private suspend fun TestFixtureInitializer.R<PyInterpreterFixture<PyEnvironment>>.initializedTestFixture(
  pyProject: PyProject,
  env: PyEnvironment,
): TestFixtureInitializer.InitializedTestFixture<PyInterpreterFixture<PyEnvironment>> {
  val interpreter = pyProject.setPythonInterpreter(env.prepareSdk(pyProject)).orThrow()!!
  return initialized(PyInterpreterFixture(interpreter, env, pyProject.project)) {
    pyProject.setPythonInterpreter(null).orThrow()
    PythonInterpreterRegistry.getInstance(pyProject.project).removePythonInterpreter(pyProject, interpreter)
    env.close()
  }
}
