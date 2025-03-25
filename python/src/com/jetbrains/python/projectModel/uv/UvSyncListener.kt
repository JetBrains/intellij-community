// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.util.messages.Topic
import java.nio.file.Path

interface UvSyncListener {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<UvSyncListener> = Topic(UvSyncListener::class.java, Topic.BroadcastDirection.NONE)
  }

  // Add onFailure
  // Add onCancel
  fun onStart(projectRoot: Path): Unit = Unit
  fun onFinish(projectRoot: Path): Unit = Unit
}