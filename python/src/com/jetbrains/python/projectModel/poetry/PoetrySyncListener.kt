// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.util.messages.Topic
import com.jetbrains.python.projectModel.ProjectModelSyncListener
import java.nio.file.Path

interface PoetrySyncListener : ProjectModelSyncListener {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<PoetrySyncListener> = Topic(PoetrySyncListener::class.java, Topic.BroadcastDirection.NONE)
  }

  // Add onFailure
  // Add onCancel
  override fun onStart(projectRoot: Path): Unit = Unit
  override fun onFinish(projectRoot: Path): Unit = Unit
}