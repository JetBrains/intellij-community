// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.io.FileUtil.fileHashCode
import com.intellij.openapi.util.io.FileUtil.filesEqual
import org.jetbrains.idea.svn.api.Url
import java.io.File

class WorkingCopy(val file: File, val url: Url) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WorkingCopy) return false

    return filesEqual(file, other.file)
  }

  override fun hashCode() = fileHashCode(file)
}
