// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootEntityEx.getSourceRoots
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

object ModuleEntityEx {
  fun ModuleEntity.getSourceRootUrls(includingTests: Boolean): List<String> =
    this.contentRoots
      .flatMap { it.getSourceRoots(includingTests) }
      .map { it.url }
      .map { it.url }
}

private object ContentRootEntityEx {
  fun ContentRootEntity.getSourceRoots(includingTests: Boolean): List<SourceRootEntity> =
    if (includingTests) {
      this.sourceRoots
    }
    else {
      this.sourceRoots.filter {
        it.rootType == JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID
        || it.rootType == JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
      }
    }
}