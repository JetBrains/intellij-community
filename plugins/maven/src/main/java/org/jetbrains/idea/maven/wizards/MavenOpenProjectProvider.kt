// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenOpenProjectProvider : AbstractOpenProjectProvider() {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  val builder: MavenProjectBuilder
    get() = ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(MavenProjectBuilder::class.java)

  override fun isProjectFile(file: VirtualFile): Boolean {
    return MavenUtil.isPomFile(file)
  }

  override fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
    cs.launch {
      withContext(Dispatchers.Default) {
        linkToExistingProjectAsync(projectFile, project)
    }
    }
  }

  override suspend fun linkToExistingProjectAsync(projectFile: VirtualFile, project: Project) {
    LOG.debug("Link Maven project '$projectFile' to existing project ${project.name}")

    if (Registry.`is`("external.system.auto.import.disabled")) return

    val projectRoot = if (projectFile.isDirectory) projectFile else projectFile.parent

    if (ExternalSystemTrustedProjectDialog.confirmLinkingUntrustedProjectAsync(project, systemId, projectRoot.toNioPath())) {
      val asyncBuilder = MavenProjectAsyncBuilder()
      asyncBuilder.commit(project, projectFile, null)
    }
  }

}