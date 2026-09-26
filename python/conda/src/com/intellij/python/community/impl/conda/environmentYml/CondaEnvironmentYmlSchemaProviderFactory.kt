// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda.environmentYml

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Associates the bundled conda environment schema with conda `environment.yml` / `environment.yaml` files
 * so they get completion and validation out of the box.
 */
@ApiStatus.Internal
class CondaEnvironmentYmlSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(CondaEnvironmentYmlSchemaProvider)
}

@ApiStatus.Internal
object CondaEnvironmentYmlSchemaProvider : JsonSchemaFileProvider {
  private const val SCHEMA_PATH = "/schemas/conda-environment.json"

  override fun isAvailable(file: VirtualFile): Boolean = file.name in CondaEnvironmentYmlSdkUtils.envFileNames

  override fun getName(): @Nls String = PyCondaBundle.message("conda.environment.yml.schema.name")

  override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(javaClass, SCHEMA_PATH)

  override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

  override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7
}
