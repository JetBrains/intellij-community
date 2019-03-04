// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

abstract class BaseNodeDescription protected constructor(open val nodeKind: NodeKind) {
  val isFile get() = nodeKind.isFile
  val isDirectory get() = nodeKind.isDirectory
  val isNone get() = nodeKind.isNone
}
