// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import org.jetbrains.idea.maven.importing.MavenImportUtil.unescapeCompileSourceRootModuleSuffix

private class MavenCompileSourceRootsConfigurator : MavenWorkspaceConfigurator {
  override fun configureMavenProject(context: MavenWorkspaceConfigurator.MutableMavenProjectContext) {
    val mavenProjectWithModules = context.mavenProjectWithModules
    val modulesWithType = mavenProjectWithModules.modules

    val compileSourceRootModules = modulesWithType.filter { it.type == StandardMavenModuleType.MAIN_ONLY_ADDITIONAL }.map { it.module }
    if (compileSourceRootModules.isEmpty()) return

    val compoundModule =  modulesWithType.firstOrNull { it.type == StandardMavenModuleType.COMPOUND_MODULE }?.module  ?: return

    val storage = context.storage
    val project = context.project
    val workspaceModel = project.workspaceModel
    val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
    val mavenProject = mavenProjectWithModules.mavenProject

    compileSourceRootModules.forEach { module ->
      val entitySource = module.entitySource
      val moduleSuffix = module.name.substring(compoundModule.name.length + 1)
      val executionId = unescapeCompileSourceRootModuleSuffix(moduleSuffix)
      val sourceRoots = MavenImportUtil.getCompileSourceRoots(mavenProject, executionId)
      sourceRoots.forEach { sourceRoot ->
        val url = virtualFileUrlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(sourceRoot))
        val contentRootEntity = ContentRootEntity(url, emptyList(), entitySource)
        val sourceRootEntity = SourceRootEntity(url, JAVA_SOURCE_ROOT_ENTITY_TYPE_ID, entitySource)
        sourceRootEntity.javaSourceRoots += JavaSourceRootPropertiesEntity(false, "", sourceRootEntity.entitySource)
        contentRootEntity.sourceRoots += sourceRootEntity
        storage.modifyModuleEntity(module) {
          this.contentRoots += contentRootEntity
        }
      }
    }
  }
}