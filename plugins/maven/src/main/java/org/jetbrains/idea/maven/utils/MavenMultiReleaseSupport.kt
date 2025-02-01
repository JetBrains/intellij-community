// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.codeInsight.daemon.impl.analysis.MultiReleaseSupport
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import org.jetbrains.idea.maven.importing.MavenImportUtil.MAIN_SUFFIX
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.jps.model.serialization.SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID

private class MavenMultiReleaseSupport : MultiReleaseSupport {
  override fun getMainMultiReleaseModule(additionalModule: Module): Module? {
    // Maven
    val project = additionalModule.project
    val storage = project.workspaceModel.currentSnapshot
    val additionalModuleName = additionalModule.name
    val additionalModuleExOptions = storage.resolve(ModuleId(additionalModuleName))?.exModuleOptions
    if (additionalModuleExOptions?.externalSystem == MAVEN_EXTERNAL_SOURCE_ID) {
      val baseModuleName = additionalModuleName.substringBeforeLast('.')
      val mainModuleName = "$baseModuleName.$MAIN_SUFFIX"
      val mainModuleExOptions = storage.resolve(ModuleId(mainModuleName))?.exModuleOptions
      if (mainModuleExOptions?.externalSystemModuleType == StandardMavenModuleType.MAIN_ONLY.toString()
      ) {
        return ModuleManager.getInstance(project).findModuleByName(mainModuleName)
      }
    }
    return null
  }
}