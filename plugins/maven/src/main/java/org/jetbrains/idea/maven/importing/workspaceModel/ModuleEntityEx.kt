// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

fun ModuleEntity.getSourceRootUrls(includingTests: Boolean): List<String> =
  this.contentRoots
    .flatMap { it.getSourceRoots(includingTests) }
    .map { it.url.url }

private fun ContentRootEntity.getSourceRoots(includingTests: Boolean): List<SourceRootEntity> =
  this.sourceRoots.filter { includingTests || !it.isTest() }

private fun SourceRootEntity.isTest(): Boolean =
  this.rootType == JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID
  || this.rootType == JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID
