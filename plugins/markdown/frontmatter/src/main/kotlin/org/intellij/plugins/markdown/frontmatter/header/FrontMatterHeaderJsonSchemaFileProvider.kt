package org.intellij.plugins.markdown.frontmatter.header

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import org.intellij.plugins.markdown.frontmatter.FrontMatterBundle
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider
import org.jetbrains.yaml.YAMLFileType

internal class FrontMatterHeaderJsonSchemaFileProvider(private val project: Project): JsonSchemaFileProvider {
  override fun isAvailable(file: VirtualFile): Boolean {
    if (!FrontMatterHeaderMarkerProvider.isFrontMatterSupportEnabled()) {
      return false
    }
    if (!FileTypeManager.getInstance().isFileOfType(file, YAMLFileType.YML)) {
      return false
    }
    return runReadAction { isInjectedFrontMatter(file) }
  }

  private fun isInjectedFrontMatter(file: VirtualFile): Boolean {
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val topLevelFile = injectedLanguageManager.getTopLevelFile(psiFile)
    return topLevelFile.language.isMarkdownLanguage()
  }

  override fun getName(): String {
    return FrontMatterBundle.message("markdown.frontmatter.header.json.schema.name")
  }

  override fun getSchemaFile(): VirtualFile {
    return LightVirtualFile("generic_front_matter_header_schema.json", schema)
  }

  override fun getSchemaType(): SchemaType {
    return SchemaType.schema
  }

  companion object {
    // language=JSON
    private const val schema = """
    {
      "properties": {
        "title": {
          "type": "string"
        },
        "layout": {
          "type": ["string", "null"]
        },
        "permalink": {
          "type": "string"
        },
        "published": {
          "type": "boolean"
        },
        "date": {
          "type": "string"
        },
        "category": {
          "type": "string"
        },
        "categories": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "uniqueItems": true
        },
        "tags": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "uniqueItems": true
        }
      }
    }
    """
  }
}
