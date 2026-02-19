// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency

import com.intellij.openapi.roots.DependencyScope

abstract class MavenImportDependency<T>(val artifact: T, val scope: DependencyScope) {
  override fun toString(): String {
    return artifact.toString()
  }
}
