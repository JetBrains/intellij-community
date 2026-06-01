// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.ArrayList
import java.util.TreeSet

@ApiStatus.Internal
class MavenProjectsManagerState {
  private var _originalFiles: MutableList<String> = ArrayList()

  var originalFiles: List<String>
    get() = _originalFiles
    set(value) {
      MavenLog.LOG.debug("setOriginalFiles: $value")
      _originalFiles = value.distinct().toMutableList()
    }

  fun addOriginalFile(file: String) {
    MavenLog.LOG.debug("addOriginalFile: $file")
    if (file !in _originalFiles) {
      _originalFiles.add(file)
    }
  }

  fun removeOriginalFiles(pathsToRemove: Set<String>) {
    MavenLog.LOG.debug("removeOriginalFiles: $pathsToRemove")
    originalFiles = originalFiles.filterNot(pathsToRemove::contains)
  }

  @JvmField
  var ignoredFiles: MutableSet<String> = TreeSet()
  @JvmField
  var ignoredPathMasks: MutableList<String> = ArrayList()

  @JvmField
  var enabledProfiles: MutableList<String> = ArrayList()
  @JvmField
  var disabledProfiles: MutableList<String> = ArrayList()
}