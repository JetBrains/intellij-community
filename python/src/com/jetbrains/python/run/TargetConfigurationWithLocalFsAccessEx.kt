// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.diagnostic.Logger


/**
 * When module sits on this target, should allow user create sdk on [confType]?
 * ``\\wsl$`` projects allow WSL and Docker
 */
fun TargetConfigurationWithLocalFsAccess.allowCreationTargetOfThisType(confType: TargetEnvironmentType<*>): Boolean {
  val javaClass = asTargetConfig.getTargetType().javaClass
  return javaClass == confType.javaClass || javaClass in confType.canProbablyRunCodeForeignTypes
}


/**
 * When module sits on this target, should allow user to choose sdk with [config]?
 * ``\\wsl$`` projects allow Docker AND only WSL with right distro
 */
fun TargetConfigurationWithLocalFsAccess.codeCouldProbablyBeRunWithConfig(config: TargetEnvironmentConfiguration?): Boolean {
  if (config == null) return false // For now no local target could run remote
  if (asTargetConfig == config) return true // Same config (like same wsl distro)
  return asTargetConfig.getTargetType().javaClass in config.getTargetType().canProbablyRunCodeForeignTypes
}

private val TargetEnvironmentType<*>.canProbablyRunCodeForeignTypes: List<Class<out TargetEnvironmentType<*>>>
  get() {
    val ep = PythonInterpreterTargetEnvironmentFactory.EP_NAME.extensionList.firstOrNull { it.getTargetType().javaClass == javaClass }
    if (ep == null) {
      Logger.getInstance(TargetEnvironmentType::class.java).warn("No EP for $this")
      return emptyList()
    }
    return ep.canProbablyRunCodeForeignTypes
  }