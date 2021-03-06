// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.containers.ContainerUtil.createSoftMap
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.browse.DirectoryEntry

class SvnRepositoryCache private constructor() {
  private val myMap = createSoftMap<Url, List<DirectoryEntry>>()
  private val myErrorsMap = createSoftMap<Url, VcsException>()

  fun getChildren(parent: Url): List<DirectoryEntry>? = myMap[parent]

  fun getError(parent: Url): VcsException? = myErrorsMap[parent]

  fun put(parent: Url, error: VcsException) {
    myMap.remove(parent)
    myErrorsMap[parent] = error
  }

  fun put(parent: Url, children: List<DirectoryEntry>) {
    myErrorsMap.remove(parent)
    myMap[parent] = children
  }

  fun remove(parent: Url) {
    myErrorsMap.remove(parent)
    myMap.remove(parent)
  }

  companion object {
    @JvmStatic
    val instance: SvnRepositoryCache
      get() = service()
  }
}
