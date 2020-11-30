package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import icons.PackageSearchIcons
import java.util.regex.Pattern

class PackageSearchQuickFix(private val ref: PsiReference) : IntentionAction, LowPriorityAction, Iconable {

  private val classnamePattern =
    Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{Lu}\\p{javaJavaIdentifierPart}+")

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    PackageSearchToolWindowFactory.activateToolWindow(project) {
      val model = project.getUserData(PackageSearchToolWindowFactory.ToolWindowModelKey)
      model?.searchTerm?.set(ref.canonicalText)
    }
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = ref.element.run {
    isValid && classnamePattern.matcher(text).matches()
  }

  override fun getText() = PackageSearchBundle.message("packagesearch.quickfix.packagesearch.action")

  override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.packagesearch.family")

  override fun getIcon(flags: Int) = PackageSearchIcons.Package

  override fun startInWriteAction() = false
}
