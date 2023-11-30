// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.projectRoots.Sdk

fun ObservableMutableProperty<Sdk?>.transformToHomePathProperty(sdks: ObservableProperty<List<Sdk>>): ObservableMutableProperty<String?> =
  transform(
    map = { sdk -> sdk?.homePath },
    backwardMap = { path -> sdks.get().find { it.homePath == path } }
  )