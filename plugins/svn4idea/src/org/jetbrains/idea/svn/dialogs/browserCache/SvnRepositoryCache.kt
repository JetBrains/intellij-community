// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache

import com.intellij.openapi.components.service
import com.intellij.util.containers.ContainerUtil.createSoftMap
import org.jetbrains.idea.svn.browse.DirectoryEntry

class SvnRepositoryCache private constructor() {
  private val myMap = createSoftMap<String, List<DirectoryEntry>>()
  private val myErrorsMap = createSoftMap<String, String>()

  fun getChildren(parent: String) = myMap[parent]

  fun getError(parent: String) = myErrorsMap[parent]

  fun put(parent: String, error: String) {
    myMap.remove(parent)
    myErrorsMap[parent] = error
  }

  fun put(parent: String, children: List<DirectoryEntry>) {
    myErrorsMap.remove(parent)
    myMap[parent] = children
  }

  fun remove(parent: String) {
    myErrorsMap.remove(parent)
    myMap.remove(parent)
  }

  companion object {
    @JvmStatic
    val instance: SvnRepositoryCache
      get() = service()
  }
}
