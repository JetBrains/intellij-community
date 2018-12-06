// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.openapi.util.Getter
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.svn.api.NodeKind
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.conflict.TreeConflictDescription
import org.jetbrains.idea.svn.info.Info
import org.jetbrains.idea.svn.lock.Lock

import java.io.File

/**
 * TODO: Could also inherit BaseNodeDescription when myNodeKind becomes final.
 */
class Status {
  var url: Url? = null
    get() {
      var url = field

      if (url == null) {
        val info = initInfo()
        url = if (info != null) info.url else url
      }

      return url
    }
  var file: File? = null
    get() {
      var file = field

      if (file == null) {
        val info = initInfo()
        file = if (info != null) info.file else file
      }

      return file
    }
  private var myFileExists: Boolean = false
  var nodeKind = NodeKind.UNKNOWN
    get() {
      if (myFileExists) return field
      val info = initInfo()
      return info?.nodeKind ?: field
    }
  var revision: Revision
    get() {
      val revision = field
      if (revision.isValid) return revision

      if (`is`(StatusType.STATUS_NONE, StatusType.STATUS_UNVERSIONED, StatusType.STATUS_ADDED)) {
        return revision
      }

      val info = initInfo()
      return info?.revision ?: revision
    }
  var committedRevision: Revision
  var itemStatus = StatusType.STATUS_NONE
  var propertyStatus = StatusType.STATUS_NONE
  var remoteItemStatus: StatusType? = null
  var remotePropertyStatus: StatusType? = null
  var isWorkingCopyLocked: Boolean = false
  var isCopied: Boolean = false
  var isSwitched: Boolean = false
  var remoteLock: Lock? = null
  var localLock: Lock? = null
  var changeListName: String? = null
  var isTreeConflicted: Boolean = false
  private var myInfo: Info? = null
  private var myInfoProvider: Getter<Info?>? = null

  val info: Info?
    get() = initInfo()

  val copyFromUrl: Url?
    get() {
      if (!isCopied) return null
      val info = initInfo()
      return info?.copyFromUrl
    }

  val treeConflict: TreeConflictDescription?
    get() {
      if (!isTreeConflicted) return null
      val info = initInfo()
      return info?.treeConflict
    }

  val repositoryRootUrl: Url?
    get() {
      val info = initInfo()
      return info?.repositoryRootUrl
    }

  init {
    revision = Revision.UNDEFINED
    myInfoProvider = Getter { null }
    committedRevision = Revision.UNDEFINED
  }

  fun setInfoProvider(infoProvider: Getter<Info?>) {
    myInfoProvider = infoProvider
  }

  private fun initInfo(): Info? {
    if (myInfo == null) {
      val itemStatus = itemStatus
      if (itemStatus == null || StatusType.STATUS_NONE == itemStatus) {
        return null
      }
      myInfo = myInfoProvider!!.get()
    }
    return myInfo
  }

  fun `is`(type: StatusType): Boolean {
    return type == itemStatus
  }

  fun `is`(vararg types: StatusType): Boolean {
    return ContainerUtil.or(types) { type -> `is`(type) }
  }

  fun isProperty(type: StatusType): Boolean {
    return type == propertyStatus
  }

  fun isProperty(vararg types: StatusType): Boolean {
    return ContainerUtil.or(types) { type -> isProperty(type) }
  }

  fun setNodeKind(exists: Boolean, nodeKind: NodeKind) {
    myFileExists = exists
    this.nodeKind = nodeKind
  }
}
