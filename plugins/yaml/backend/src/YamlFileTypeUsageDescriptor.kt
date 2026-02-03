// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor
import com.intellij.lang.LighterAST
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.util.AstLoadingFilter
import com.intellij.util.concurrency.annotations.RequiresReadLock

private val SCHEMA_KEY: Key<ImportantSchema> = Key.create("YAML_USAGE_SCHEMA")

internal class K8sFileTypeUsageDescriptor : ImportantFileTypeUsageDescriptor(ImportantSchema.KUBERNETES)
internal class OpenapiFileTypeUsageDescriptor : ImportantFileTypeUsageDescriptor(ImportantSchema.OPENAPI)
internal class SwaggerFileTypeUsageDescriptor : ImportantFileTypeUsageDescriptor(ImportantSchema.SWAGGER)
internal class DockerComposeFileTypeUsageDescriptor : ImportantFileTypeUsageDescriptor(ImportantSchema.DOCKER_COMPOSE)
internal class CloudFormationFileTypeUsageDescriptor : ImportantFileTypeUsageDescriptor(ImportantSchema.CLOUD_FORMATION)

internal open class ImportantFileTypeUsageDescriptor(private val schema: ImportantSchema) : FileTypeUsageSchemaDescriptor {
  override fun describes(project: Project, file: VirtualFile): Boolean {
    if (file.fileType != YAMLFileType.YML) return false

    // do not run read actions once we computed initial value
    file.getUserData(SCHEMA_KEY)?.let { return it == schema }

    val marker = ReadAction.nonBlocking<ImportantSchema> {
      if (project.isDisposed) return@nonBlocking ImportantSchema.NONE

      getSchema(project, file)
    }.executeSynchronously()

    file.putUserData(SCHEMA_KEY, marker)
    return marker == schema
  }
}

internal enum class ImportantSchema {
  CLOUD_FORMATION,
  DOCKER_COMPOSE,
  KUBERNETES,
  OPENAPI,
  SWAGGER,
  NONE
}

@RequiresReadLock
internal fun getSchema(project: Project, file: VirtualFile): ImportantSchema {
  val viewProvider = PsiManager.getInstance(project).findViewProvider(file)
  if (viewProvider == null) return ImportantSchema.NONE

  val files = viewProvider.allFiles
  for (f in files) {
    if (f is PsiFileImpl && f.fileType == YAMLFileType.YML) {
      val astTree = AstLoadingFilter.forceAllowTreeLoading<FileElement, Throwable>(f) {
        f.calcTreeElement()
      }

      return detect(astTree.lighterAST)
    }
  }
  return ImportantSchema.NONE
}

private fun detect(tree: LighterAST): ImportantSchema {
  var hasApiVersion = false
  var hasKind = false

  var hasOpenapi = false
  var hasSwagger = false

  var hasServices = false

  var hasResources = false
  var hasAwsFormat = false

  var schema: ImportantSchema? = null

  visitTopLevelKeyPairs(tree) { key, _ ->
    when (key) {
      "kind" -> hasKind = true
      "apiVersion" -> hasApiVersion = true
      "openapi" -> hasOpenapi = true
      "swagger" -> hasSwagger = true
      "services" -> hasServices = true
      "Resources" -> hasResources = true
      "AWSTemplateFormatVersion" -> hasAwsFormat = true
    }

    schema = when {
      hasOpenapi -> ImportantSchema.OPENAPI
      hasSwagger -> ImportantSchema.SWAGGER
      hasApiVersion && hasKind -> ImportantSchema.KUBERNETES
      hasServices -> ImportantSchema.DOCKER_COMPOSE
      hasResources || hasAwsFormat -> ImportantSchema.CLOUD_FORMATION
      else -> null
    }

    schema == null
  }

  return schema ?: ImportantSchema.NONE
}
