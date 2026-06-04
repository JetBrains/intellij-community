package com.intellij.mermaid.lang.injection

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

class FrontmatterYamlSchemaProviderFactory : JsonSchemaProviderFactory {
  override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
    return mutableListOf(FrontmatterYamlSchemaFileProvider(project))
  }
}
