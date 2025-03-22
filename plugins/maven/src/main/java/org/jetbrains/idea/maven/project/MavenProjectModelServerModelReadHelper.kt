// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

open class MavenProjectModelServerModelReadHelper(protected val myProject: Project) : MavenProjectModelReadHelper {
  override fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String> {
    return modules;
  }
}
