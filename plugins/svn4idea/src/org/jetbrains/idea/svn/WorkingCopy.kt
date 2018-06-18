// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import org.jetbrains.idea.svn.api.Url

import java.io.File

// TODO: is17Copy is currently true in all constructor usages - remove this field and revise is17Copy() usages
class WorkingCopy(val file: File, val url: Url, val is17Copy: Boolean) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WorkingCopy) return false

    return file == other.file
  }

  override fun hashCode() = file.hashCode()
}
