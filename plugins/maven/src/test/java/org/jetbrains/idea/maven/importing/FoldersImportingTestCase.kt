// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenFolderResolver

abstract class FoldersImportingTestCase : MavenMultiVersionImportingTestCase() {

  override fun setUp() {
    super.setUp()
    projectsManager.initForTests()
    projectsManager.listenForExternalChanges()
  }

  protected suspend fun resolveFoldersAndImport() {
    MavenFolderResolver(projectsManager.project).resolveFoldersAndImport(projectsManager.projects)
  }

  protected fun createProjectSubDirsWithFile(vararg dirs: String) {
    for (dir in dirs) {
      createProjectSubFile("$dir/a.txt")
    }
  }
}