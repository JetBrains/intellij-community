// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.interpreters

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.TargetAndPath
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.detectSdkPaths
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.targetEnvConfiguration
import com.jetbrains.python.sdk.tryFindPythonBinaries
import kotlin.io.path.pathString


fun detectSystemInterpreters(projectDir: String?, module: Module?, targetConfiguration: TargetEnvironmentConfiguration?, existingSdks: List<Sdk>): List<DetectedSelectableInterpreter> {
  return if (targetConfiguration == null) detectLocalSystemInterpreters(module, existingSdks)
  else detectSystemInterpretersOnTarget(targetConfiguration)
}

fun detectSystemInterpretersOnTarget(targetConfiguration: TargetEnvironmentConfiguration): List<DetectedSelectableInterpreter> {
  val targetWithMappedLocalVfs = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(targetConfiguration)
  if (targetWithMappedLocalVfs != null) {
    val searchRoots = listOf("/usr/bin/", "/usr/local/bin/")
    return searchRoots.flatMap { searchRoot ->
      targetWithMappedLocalVfs.getLocalPath(searchRoot)?.tryFindPythonBinaries()?.mapNotNull {
        val pythonBinaryPath = targetWithMappedLocalVfs.getTargetPath(it) ?: return@mapNotNull null
        DetectedSelectableInterpreter(pythonBinaryPath, targetConfiguration)
      } ?: emptyList()
    }
  }
  else {
    // TODO Try to execute `which python` or introspect the target
    //val request = targetEnvironmentConfiguration.createEnvironmentRequest(project = null)
    //request.prepareEnvironment(TargetProgressIndicator.EMPTY).createProcess()
    return emptyList()
  }
}



// todo
//  remove context -- it's only platform independent flavours
//
fun detectLocalSystemInterpreters(module: Module?, existingSdks: List<Sdk>): List<DetectedSelectableInterpreter> {

  if (module != null && module.isDisposed) return emptyList()
  val existingPaths = existingSdks.mapTo(HashSet()) { it.homePath }

  return PythonSdkFlavor.getApplicableFlavors(false)
    .asSequence()
    .flatMap { it.suggestLocalHomePaths(module, null) }
    .map { it.pathString }
    .filter { it !in existingPaths }
    .map { DetectedSelectableInterpreter(it) }
    .toList()

    // todo sort

  //return PythonSdkFlavor.getApplicableFlavors(false)
  //
  //  .flatMap { flavor -> flavor.detectInterpreters(module, context, targetModuleSitsOn, existingPaths) } // sorting
  //.sortedWith(compareBy<PyDetectedSdk>({ it.guessedLanguageLevel },
  //                                     { it.homePath }).reversed())
}


private fun PythonSdkFlavor<*>.detectInterpreters(module: Module?,
                                                  context: UserDataHolder,
                                                  targetModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
                                                  existingPaths: HashSet<TargetAndPath>): List<DetectedSelectableInterpreter> {
  return detectSdkPaths(module, context, targetModuleSitsOn, existingPaths)
    .map { DetectedSelectableInterpreter(it, targetModuleSitsOn?.asTargetConfig) }
}