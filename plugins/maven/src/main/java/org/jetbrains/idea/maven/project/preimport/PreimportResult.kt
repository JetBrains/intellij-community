// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.preimport

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsTree


class PreimportResult(val modules: List<Module>, val projectTree: MavenProjectsTree) {
  companion object {
    fun empty(project: Project): PreimportResult = PreimportResult(emptyList(), MavenProjectsTree(project))
  }
}