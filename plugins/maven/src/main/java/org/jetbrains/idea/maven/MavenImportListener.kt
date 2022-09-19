// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface MavenImportListener {
  fun importStarted(project: Project)
  fun importSucceed(project: Project)
  fun importCancelled(project: Project)
  fun importFailed(project: Project, problems: MavenProjectProblems)

  companion object {
    val MAVEN_IMPORT_EP = ExtensionPointName<MavenVersionAwareSupportExtension>("org.jetbrains.idea.maven.importListener")
  }
}

data class MavenProjectProblems(val error: Throwable, val message: String)