// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.resolvePath
import org.jetbrains.idea.svn.api.BaseNodeDescription
import org.jetbrains.idea.svn.api.NodeKind
import java.io.File
import javax.xml.bind.annotation.*

@Nls
private fun ConflictReason.getDisplayName(): String =
  when (this) {
    ConflictReason.EDITED -> message("conflict.reason.edited")
    ConflictReason.OBSTRUCTED -> message("conflict.reason.obstructed")
    ConflictReason.DELETED -> message("conflict.reason.deleted")
    ConflictReason.MISSING -> message("conflict.reason.missing")
    ConflictReason.UNVERSIONED -> message("conflict.reason.unversioned")
    ConflictReason.ADDED -> message("conflict.reason.added")
    ConflictReason.REPLACED -> message("conflict.reason.replaced")
    ConflictReason.MOVED_AWAY -> message("conflict.reason.moved.away")
    ConflictReason.MOVED_HERE -> message("conflict.reason.moved.here")
  }

@Nls
private fun ConflictAction.getDisplayName(): String =
  when (this) {
    ConflictAction.EDIT -> message("conflict.action.edit")
    ConflictAction.ADD -> message("conflict.action.add")
    ConflictAction.DELETE -> message("conflict.action.delete")
    ConflictAction.REPLACE -> message("conflict.action.replace")
  }

@Nls
private fun ConflictOperation.getDisplayName(): String =
  when (this) {
    ConflictOperation.UPDATE -> message("conflict.operation.update")
    ConflictOperation.SWITCH -> message("conflict.operation.switch")
    ConflictOperation.MERGE -> message("conflict.operation.merge")
    ConflictOperation.NONE -> message("conflict.operation.none")
  }

class TreeConflictDescription private constructor(builder: Builder, base: File) : BaseNodeDescription(builder.kind) {
  val path = resolvePath(base, builder.path)
  val conflictAction = builder.action
  val conflictReason = builder.reason
  val operation = builder.operation
  val sourceLeftVersion = builder.sourceLeftVersion
  val sourceRightVersion = builder.sourceRightVersion

  @Nls
  fun toPresentableString(): String =
    message("tree.conflict.description", conflictReason.getDisplayName(), conflictAction.getDisplayName(), operation.getDisplayName())

  @XmlAccessorType(XmlAccessType.NONE)
  @XmlType(name = "tree-conflict")
  @XmlRootElement(name = "tree-conflict")
  class Builder {
    @XmlAttribute(name = "victim")
    var path = ""

    @XmlAttribute
    var kind = NodeKind.UNKNOWN

    @XmlAttribute
    var operation = ConflictOperation.NONE

    @XmlAttribute
    var action = ConflictAction.ADD

    @XmlAttribute
    var reason = ConflictReason.ADDED

    @XmlElement(name = "version")
    private val versions = mutableListOf<ConflictVersionWithSide>()

    val sourceLeftVersion get() = versions.find { it.side == SOURCE_LEFT_SIDE }?.build()
    val sourceRightVersion get() = versions.find { it.side == SOURCE_RIGHT_SIDE }?.build()

    fun build(base: File) = TreeConflictDescription(this, base)

    companion object {
      @NonNls private const val SOURCE_LEFT_SIDE = "source-left"
      @NonNls private const val SOURCE_RIGHT_SIDE = "source-right"
    }
  }
}

@XmlAccessorType(XmlAccessType.NONE)
@XmlType(name = "version")
@XmlRootElement(name = "version")
private class ConflictVersionWithSide {
  @XmlAttribute
  var side = ""

  @XmlAttribute
  var kind = NodeKind.UNKNOWN

  @XmlAttribute(name = "path-in-repos")
  var path = ""

  @XmlAttribute(name = "repos-url")
  var repositoryRoot = ""

  @XmlAttribute(name = "revision")
  var revisionNumber = -1L

  fun build() = ConflictVersion(createUrl(repositoryRoot), path, revisionNumber, kind)
}