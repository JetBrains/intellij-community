package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService
import java.util.regex.Pattern

class PackageSearchUnresolvedReferenceQuickFix(private val ref: PsiReference) : IntentionAction, LowPriorityAction, Iconable {

    private val classnamePattern =
        Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{Lu}\\p{javaJavaIdentifierPart}+")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        PackageSearchToolWindowFactory.activateToolWindow(project) {
            project.packageSearchDataService
                .setSearchQuery(ref.canonicalText)
            PackageSearchEventsLogger.logRunQuickFix(FUSGroupIds.QuickFixTypes.UnresolvedReference, file?.fileType?.name)
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = ref.element.run {
        isValid && classnamePattern.matcher(text).matches()
    }

    @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name...
    override fun getText() = PackageSearchBundle.message("packagesearch.quickfix.packagesearch.action")

    @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name...
    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.packagesearch.family")

    override fun getIcon(flags: Int) = PackageSearchIcons.Package

    override fun startInWriteAction() = false
}
