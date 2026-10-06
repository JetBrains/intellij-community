// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.filter

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.sdkMatchesEel
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.codeCouldProbablyBeRunWithConfig
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.targetEnvConfiguration
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Filters and sorts [sdks] the same way [com.jetbrains.python.sdk.getAssignablePythonSdks] does. The "Python Interpreters" dialog
 * passes the editable copies from its own `ProjectSdksModel` here, so the displayed list matches the live one.
 */
@Internal
fun ModuleOrProject.filterAssignablePythonSdks(sdks: Collection<Sdk>): List<Sdk> =
  SdkFilter.OnModuleOrProject(this).filterAndSort(sdks)

/**
 * Returns the Python SDKs from [sdks] that can run on this eel, sorted as [ModuleOrProject.filterAssignablePythonSdks] does.
 *
 * If the machine of this eel is not resolved yet, a target-based SDK is kept only if a [TargetSupportsFilterExtension] supports it.
 */
@Internal
fun EelDescriptor.filterAssignablePythonSdks(sdks: Collection<Sdk>): List<Sdk> =
  SdkFilter.OnEelDescriptor(this).filterAndSort(sdks)

/**
 * Each filter gets its eel machine and target one time, not one time for each SDK.
 */
private sealed interface SdkFilter {
  class OnModuleOrProject(moduleOrProject: ModuleOrProject) : SdkFilter {
    val eelMachine: EelMachine = moduleOrProject.project.getEelMachine()
    val targetModuleSitsOn: TargetConfigurationWithLocalFsAccess? =
      moduleOrProject.moduleIfExists?.let { PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(it) }
  }

  class OnEelDescriptor(val eelDescriptor: EelDescriptor) : SdkFilter {
    val resolvedMachine: EelMachine? = eelDescriptor.getResolvedEelMachine()
  }
}

private fun SdkFilter.filterAndSort(sdks: Collection<Sdk>): List<Sdk> =
  sdks.filter { PythonSdkUtil.isPythonSdk(it) && it.canBeUsedWith(this) }
    .sortedWith(compareBy({ PythonSdkUtil.isRemote(it) }, { it.name }))

private fun Sdk.canBeUsedWith(filter: SdkFilter): Boolean = when (filter) {
  is SdkFilter.OnModuleOrProject -> {
    val targetModuleSitsOn = filter.targetModuleSitsOn
    sdkMatchesEel(filter.eelMachine, this) &&
    (targetModuleSitsOn == null || targetModuleSitsOn.codeCouldProbablyBeRunWithConfig(targetEnvConfiguration))
  }
  is SdkFilter.OnEelDescriptor -> {
    val machine = filter.resolvedMachine
    val target = targetEnvConfiguration
    when {
      machine != null -> sdkMatchesEel(machine, this)
      target != null -> TargetSupportsFilterExtension.supports(target, filter.eelDescriptor)
      else -> Path.of(homePath!!).getEelDescriptor() == filter.eelDescriptor
    }
  }
}
