// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import kotlinx.coroutines.CoroutineScope

class PythonAddInterpreterState(
  val propertyGraph: PropertyGraph, // todo move to presenter
  val projectPath: ObservableProperty<String>,
  val scope: CoroutineScope,
  // todo replace with flow, local properties for every creator
  val allExistingSdks: ObservableMutableProperty<List<Sdk>>, // todo merge with allSdks, replace with flow and local properties
  val installableSdks: ObservableMutableProperty<List<Sdk>>, // todo not needed
  val condaExecutable: ObservableMutableProperty<String>,
) {
  internal val allSdks: ObservableMutableProperty<List<Sdk>> = propertyGraph.property(initial = allExistingSdks.get())

}