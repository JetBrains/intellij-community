// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface MavenProjectModelReadHelper {
  fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String>

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<MavenProjectModelReadHelper>()
  }
}
