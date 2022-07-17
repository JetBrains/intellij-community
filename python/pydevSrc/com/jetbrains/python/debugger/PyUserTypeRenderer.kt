// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

class PyUserTypeRenderer(
  val toType: String,
  val typeCanonicalImportPath: String,
  val typeQualifiedName: String,
  val typeSourceFile: String,
  val moduleRootHasOneTypeWithSameName: Boolean,
  val isDefaultValueRenderer: Boolean,
  val expression: String,
  val isDefaultChildrenRenderer: Boolean,
  val isAppendDefaultChildren: Boolean,
  val children: List<ChildInfo>
) {
  data class ChildInfo(val expression: String)
}