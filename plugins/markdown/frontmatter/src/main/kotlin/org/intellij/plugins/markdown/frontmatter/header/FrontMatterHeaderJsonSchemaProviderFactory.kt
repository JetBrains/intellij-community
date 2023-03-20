package org.intellij.plugins.markdown.frontmatter.header

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider

internal class FrontMatterHeaderJsonSchemaProviderFactory: JsonSchemaProviderFactory {
  override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
    if (!FrontMatterHeaderMarkerProvider.isFrontMatterSupportEnabled()) {
      return mutableListOf()
    }
    return mutableListOf(FrontMatterHeaderJsonSchemaFileProvider(project))
  }
}
