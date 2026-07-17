// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.fileActions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownFileType

private const val MARKDOWN_TEMPLATE_NAME = "Markdown File"
private const val README_FILE_NAME = "README.md"
private const val AGENTS_FILE_NAME = "AGENTS.md"
private const val SKILL_FILE_NAME = "SKILL.md"

internal open class MarkdownCreateFileAction : CreateFileFromTemplateAction(), DumbAware {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    val icon = MarkdownFileType.INSTANCE.icon
    builder
      .setTitle(MarkdownBundle.message("dialog.title.new.markdown.file"))
      .addKind(MarkdownBundle.message("dialog.kind.markdown.file"), icon, MARKDOWN_TEMPLATE_NAME)
      .addKind(README_FILE_NAME, icon, README_FILE_NAME)
      .addKind(AGENTS_FILE_NAME, icon, AGENTS_FILE_NAME)
      .addKind(SKILL_FILE_NAME, icon, SKILL_FILE_NAME)
  }

  override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile? = super.createFile(name, templateName, dir)

  override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
    MarkdownBundle.message("action.name.create.markdown.file.0", newName)
}
