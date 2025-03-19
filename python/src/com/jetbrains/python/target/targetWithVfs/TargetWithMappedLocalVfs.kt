// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target.targetWithVfs

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

interface TargetWithMappedLocalVfs {
  fun getVfsFromTargetPath(targetPath: String): VirtualFile?
  fun getTargetPathFromVfs(file: VirtualFile): String?
  fun getLocalPath(targetPath: String): Path?
  fun getTargetPath(localPath: Path): String?
}