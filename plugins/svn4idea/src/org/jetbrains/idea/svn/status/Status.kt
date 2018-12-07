// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.openapi.util.Getter
import org.jetbrains.idea.svn.api.NodeKind
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.info.Info
import org.jetbrains.idea.svn.lock.Lock
import java.io.File

/**
 * TODO: Could also inherit BaseNodeDescription when myNodeKind becomes final.
 */
class Status {
  var infoProvider = Getter<Info?> { null }
  val info by lazy { if (itemStatus != StatusType.STATUS_NONE) infoProvider.get() else null }

  var url: Url? = null
    get() = field ?: info?.url

  var file: File? = null
    get() = field ?: info?.file

  private var myFileExists = false

  var nodeKind = NodeKind.UNKNOWN
    get() = if (myFileExists) field else info?.nodeKind ?: field

  fun setNodeKind(exists: Boolean, nodeKind: NodeKind) {
    myFileExists = exists
    this.nodeKind = nodeKind
  }

  var revision = Revision.UNDEFINED
    get() {
      val revision = field

      return if (revision.isValid || `is`(StatusType.STATUS_NONE, StatusType.STATUS_UNVERSIONED, StatusType.STATUS_ADDED)) revision
      else info?.revision ?: revision
    }

  val copyFromUrl get() = if (isCopied) info?.copyFromUrl else null
  val treeConflict get() = if (isTreeConflicted) info?.treeConflict else null
  val repositoryRootUrl get() = info?.repositoryRootUrl

  var committedRevision = Revision.UNDEFINED
  var itemStatus = StatusType.STATUS_NONE
  var propertyStatus = StatusType.STATUS_NONE
  var remoteItemStatus: StatusType? = null
  var remotePropertyStatus: StatusType? = null
  var isWorkingCopyLocked = false
  var isCopied = false
  var isSwitched = false
  var remoteLock: Lock? = null
  var localLock: Lock? = null
  var changeListName: String? = null
  var isTreeConflicted = false

  fun `is`(vararg types: StatusType) = itemStatus in types
  fun isProperty(vararg types: StatusType) = propertyStatus in types
}
