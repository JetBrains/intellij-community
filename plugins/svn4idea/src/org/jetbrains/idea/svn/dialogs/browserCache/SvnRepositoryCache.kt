// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs.browserCache

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.browse.DirectoryEntry

@Service
class SvnRepositoryCache private constructor() {
  private val myMap = CollectionFactory.createSoftMap<Url, List<DirectoryEntry>>()
  private val myErrorsMap = CollectionFactory.createSoftMap<Url, VcsException>()

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
