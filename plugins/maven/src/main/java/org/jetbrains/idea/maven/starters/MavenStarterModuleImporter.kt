// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.starters

import com.intellij.ide.impl.StarterProjectConfigurator
import com.intellij.ide.starters.StarterModuleImporter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProjectsManager


internal class MavenStarterProjectConfigurator : StarterProjectConfigurator {
  override fun configureCreatedProject(project: Project) {
    MavenProjectsManager.setupCreatedMavenProject(project)
  }
}

internal class MavenStarterModuleImporter : StarterModuleImporter {
  override val id: String = "maven"
  override val title: String = "Maven"

  override fun runAfterSetup(module: Module): Boolean {
    val project = module.project
    val pomXMLs = mutableListOf<VirtualFile>()

    for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
      collectPomXml(contentRoot, pomXMLs)
      if (pomXMLs.isNotEmpty()) break
    }

    if (pomXMLs.isEmpty()) {
      for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
        for (child in contentRoot.children) {
          if (child.isDirectory) {
            collectPomXml(child, pomXMLs)
          }
        }
      }
    }

    if (pomXMLs.isEmpty()) {
      return true
    }

    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    mavenProjectsManager.addManagedFiles(pomXMLs)
    return false
  }

  private fun collectPomXml(directoryFrom: VirtualFile, collectionInto: MutableCollection<VirtualFile>) {
    val child = directoryFrom.findChild("pom.xml")
    if (child != null) {
      collectionInto.add(child)
    }
  }
}