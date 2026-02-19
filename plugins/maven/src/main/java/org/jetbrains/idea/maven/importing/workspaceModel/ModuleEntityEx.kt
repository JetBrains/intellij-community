// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID

fun ModuleEntity.getSourceRootUrls(includingTests: Boolean): List<String> =
  this.contentRoots
    .flatMap { it.getSourceRoots(includingTests) }
    .map { it.url.url }

private fun ContentRootEntity.getSourceRoots(includingTests: Boolean): List<SourceRootEntity> =
  this.sourceRoots.filter { includingTests || !it.isTest() }

private fun SourceRootEntity.isTest(): Boolean =
  this.rootTypeId == JAVA_TEST_ROOT_ENTITY_TYPE_ID
  || this.rootTypeId == JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
