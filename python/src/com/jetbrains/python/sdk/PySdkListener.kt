// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PySdkListener {
  @ApiStatus.Internal
  fun moduleSdkUpdated(module: Module, sdk: Sdk?)

  companion object {
    @ApiStatus.Internal
    val TOPIC: Topic<PySdkListener> = Topic.create("Python SDK listener", PySdkListener::class.java)
  }
}