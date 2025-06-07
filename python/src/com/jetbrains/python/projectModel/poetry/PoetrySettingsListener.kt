// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.util.messages.Topic
import java.nio.file.Path

// TODO Actions for linking/unlinking pyproject.toml files
interface PoetrySettingsListener {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<PoetrySettingsListener> = Topic(PoetrySettingsListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun onLinkedProjectAdded(projectRoot: Path): Unit = Unit
  fun onLinkedProjectRemoved(projectRoot: Path): Unit = Unit
}