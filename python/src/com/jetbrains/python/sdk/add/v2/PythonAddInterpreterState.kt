// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import kotlinx.coroutines.CoroutineScope

class PythonAddInterpreterState(
  val propertyGraph: PropertyGraph,
  val projectPath: ObservableProperty<String>,
  val scope: CoroutineScope,
  val basePythonSdks: ObservableMutableProperty<List<Sdk>>,
  val allExistingSdks: ObservableMutableProperty<List<Sdk>>,
  val basePythonVersion: ObservableMutableProperty<Sdk?>,
  val condaExecutable: ObservableMutableProperty<String>,
) {
  val basePythonHomePath = basePythonVersion.transform({ sdk -> sdk?.homePath ?: "" },
                                                       { path -> basePythonSdks.get().find { it.homePath == path }!! })

  val basePythonHomePaths = transformSdksToHomePath(basePythonSdks)
  val allValidSdkPaths = transformSdksToHomePath(allExistingSdks)





  private fun transformSdksToHomePath(sdkList: ObservableMutableProperty<List<Sdk>>): ObservableMutableProperty<List<String>> {
    return sdkList.transform({ sdks -> sdks.map { it.homePath!! } },
                             { paths ->
                               val existing = sdkList.get()
                               paths.map { path ->
                                 existing.find { it.homePath == path } ?: createDetectedSdk(path, isLocal = true)
                               }
                             })
  }
}