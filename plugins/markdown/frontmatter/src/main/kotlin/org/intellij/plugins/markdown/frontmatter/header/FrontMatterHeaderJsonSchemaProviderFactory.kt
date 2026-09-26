package org.intellij.plugins.markdown.frontmatter.header

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

internal class FrontMatterHeaderJsonSchemaProviderFactory: JsonSchemaProviderFactory {
  override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
    return mutableListOf(FrontMatterHeaderJsonSchemaFileProvider(project))
  }
}
