// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

internal class MavenModuleImportContext(
  val allModules: List<MavenTreeModuleImportData>,
  val hasChanges: Boolean
)
