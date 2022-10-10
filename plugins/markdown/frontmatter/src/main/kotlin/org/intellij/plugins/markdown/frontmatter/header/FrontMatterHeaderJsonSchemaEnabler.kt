package org.intellij.plugins.markdown.frontmatter.header

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider

internal class FrontMatterHeaderJsonSchemaEnabler : JsonSchemaEnabler {
  override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
    return file.fileType == MarkdownFileType.INSTANCE
           && FrontMatterHeaderMarkerProvider.isFrontMatterSupportEnabled()
  }
}
