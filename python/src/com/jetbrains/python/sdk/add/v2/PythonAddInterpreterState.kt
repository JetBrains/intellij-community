// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.projectRoots.Sdk
import kotlinx.coroutines.CoroutineScope

class PythonAddInterpreterState(
  val propertyGraph: PropertyGraph,
  val projectPath: ObservableProperty<String>,
  val scope: CoroutineScope,
  val basePythonSdks: ObservableMutableProperty<List<Sdk>>,
  val allExistingSdks: ObservableMutableProperty<List<Sdk>>,
  val basePythonVersion: ObservableMutableProperty<Sdk?>,
  val selectedVenv: ObservableMutableProperty<Sdk?>,
  val condaExecutable: ObservableMutableProperty<String>,
) {
  val basePythonHomePath = basePythonVersion.transform({ sdk -> sdk?.homePath ?: "" },
                                                       { path -> basePythonSdks.get().find { it.homePath == path }!! })

  val selectedVenvPath = selectedVenv.transform({ sdk -> sdk?.homePath ?: "" },
                                                { path -> allExistingSdks.get().find { it.homePath == path }!! })
}