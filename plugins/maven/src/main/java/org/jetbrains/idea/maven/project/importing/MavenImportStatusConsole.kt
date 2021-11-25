// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.build.SyncViewManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole

@Suppress("HardCodedStringLiteral")
class MavenImportStatusConsole(val project: Project) {
  val  console = MavenSyncConsole(project)
  fun start() {
    console.startImport(project.getService(SyncViewManager::class.java))
    console.addWarning("New import running", "Maven import works via new importing process")

  }

  fun finish() {
    console.finishImport()
  }
}