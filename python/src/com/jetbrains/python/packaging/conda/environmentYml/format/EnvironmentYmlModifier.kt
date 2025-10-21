// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.environmentYml.format

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object EnvironmentYmlModifier {
  private val INDENT_PATTERN = Regex("\\n(\\s+)- ", RegexOption.MULTILINE)
  private val NEXT_SESSION_PATTERN = Regex("\\n\\w+:", RegexOption.MULTILINE)

  fun addRequirement(project: Project, file: VirtualFile, packageName: String): Boolean {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false

    val pyRequirements = CondaEnvironmentYmlParser.fromFile(file) ?: return false
    if (pyRequirements.any { it.name == packageName }) {
      return false
    }
    val text = document.text
    val modifiedText = insertDependency(text, packageName)

    // Write the modified content back to the file
    @Suppress("DialogTitleCapitalization")
    WriteCommandAction.runWriteCommandAction(project, PyBundle.message("command.name.add.package.to.conda.environments.yml"), null, {
      document.setText(modifiedText)
      FileDocumentManager.getInstance().saveDocument(document)
    }, PsiManager.getInstance(project).findFile(file))

    return true
  }


  private fun insertDependency(text: @NlsSafe String, packageName: String): String {
    // Create modified YAML content with the new dependency
    val modifiedContent = StringBuilder(text)


    // Find the end of the dependencies section to add the new package
    val dependenciesText = "dependencies:"
    val dependenciesIndex = text.indexOf(dependenciesText)

    if (dependenciesIndex < 0) {
      modifiedContent.append("\ndependencies:\n  - $packageName")
    }

    if (dependenciesIndex >= 0) {
      // Find the indentation level by looking at existing entries
      val indentPattern = INDENT_PATTERN
      val indentMatch = indentPattern.find(text.substring(dependenciesIndex))
      val indent = indentMatch?.groupValues?.get(1) ?: "  " // Default to 2 spaces if no existing entries

      // Find where to insert the new dependency
      var insertIndex = dependenciesIndex + dependenciesText.length

      // Find the end of the dependencies section
      val nextSectionPattern = NEXT_SESSION_PATTERN
      val nextSectionMatch = nextSectionPattern.find(text, insertIndex)

      if (nextSectionMatch != null) {
        insertIndex = nextSectionMatch.range.first
      }
      else {
        insertIndex = text.length
      }

      // Insert the new dependency
      val newDependencyLine = "\n$indent- $packageName"
      modifiedContent.insert(insertIndex, newDependencyLine)
    }

    return modifiedContent.toString()
  }
}
