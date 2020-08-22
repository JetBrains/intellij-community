// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

internal class MavenOpenProjectProvider : AbstractOpenProjectProvider() {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  val builder: MavenProjectBuilder
    get() = ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(MavenProjectBuilder::class.java)

  override fun isProjectFile(file: VirtualFile): Boolean {
    return MavenUtil.isPomFile(file)
  }

  override fun linkAndRefreshProject(projectDirectory: Path, project: Project) {
    val builder = builder
    try {
      builder.isUpdate = false
      builder.setFileToImport(projectDirectory)
      if (builder.validate(null, project)) {
        builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
      }
    }
    finally {
      builder.cleanup()
    }
  }
}