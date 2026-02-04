// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.externalIndex.workspace

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

internal interface PyExternalIndexedFileEntity : WorkspaceEntity {
  val file: VirtualFileUrl
}

internal object PyExternalIndexedFileEntitySource : EntitySource
