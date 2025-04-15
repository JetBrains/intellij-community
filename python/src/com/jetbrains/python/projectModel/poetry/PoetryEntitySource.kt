// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/** 
 * Identifies workspace model entities managed by Poetry.
 */
class PoetryEntitySource(val projectPath: VirtualFileUrl) : EntitySource {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PoetryEntitySource

    return projectPath == other.projectPath
  }

  override fun hashCode(): Int {
    return projectPath.hashCode()
  }
}