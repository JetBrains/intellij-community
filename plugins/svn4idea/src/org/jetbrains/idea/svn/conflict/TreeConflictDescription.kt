// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict

import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.resolvePath
import org.jetbrains.idea.svn.api.BaseNodeDescription
import org.jetbrains.idea.svn.api.NodeKind
import java.io.File
import javax.xml.bind.annotation.*

class TreeConflictDescription private constructor(builder: Builder, base: File) : BaseNodeDescription(builder.kind) {
  val path = resolvePath(base, builder.path)
  val conflictAction = builder.action
  val conflictReason = builder.reason
  val operation = builder.operation
  val sourceLeftVersion = builder.sourceLeftVersion
  val sourceRightVersion = builder.sourceRightVersion

  fun toPresentableString() = "local $conflictReason, incoming $conflictAction upon $operation"

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

    val sourceLeftVersion get() = versions.find { it.side == "source-left" }?.build()
    val sourceRightVersion get() = versions.find { it.side == "source-right" }?.build()

    fun build(base: File) = TreeConflictDescription(this, base)
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