// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5.conda

import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.test.env.conda.CondaPyEnvironment
import com.intellij.python.test.env.junit5.RunOnEnvironmentsExtension
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Extension that provides [PyCondaEnv] parameter annotated with [CondaEnv].
 * Retrieves the Conda environment initialized by [RunOnEnvironmentsExtension] with conda tags.
 */
@Internal
internal class CondaPythonEnvExtension : ParameterResolver {

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type == PyCondaEnv::class.java &&
           parameterContext.parameter.isAnnotationPresent(CondaEnv::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): PyCondaEnv {
    val pyEnv = RunOnEnvironmentsExtension.getPythonEnvironment(extensionContext)
    
    // Unwrap cached environment to get the real CondaPyEnvironment
    val condaEnv = pyEnv.unwrap<CondaPyEnvironment>()
      ?: throw IllegalStateException(
        "Environment is not a Conda environment (actual type: ${pyEnv::class.qualifiedName}). " +
        "Make sure @PyEnvTestCaseWithConda or @PyEnvTestCase(tags=[\"conda\"]) is used."
      )
    
    return PyCondaEnv(
      envIdentity = PyCondaEnvIdentity.UnnamedEnv(condaEnv.envPath.toString(), isBase = false),
      fullCondaPathOnTarget = condaEnv.condaExecutable.toString()
    )
  }
}