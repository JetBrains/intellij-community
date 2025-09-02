// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.requirements.RequirementsUtil.isRequirementsFile

internal class RequirementsFileTypeOverrider : FileTypeOverrider {
  override fun getOverriddenFileType(file: VirtualFile): FileType? {
    return if (isRequirementsFile(file)) RequirementsFileType.INSTANCE else null
  }
}