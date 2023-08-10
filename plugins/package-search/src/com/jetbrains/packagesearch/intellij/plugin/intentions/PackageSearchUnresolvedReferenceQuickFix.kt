/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.dependencytoolwindow.DependencyToolWindowOpener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackagesListPanelProvider
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.pkgsUiStateModifier

class PackageSearchUnresolvedReferenceQuickFix(private val ref: PsiReference) : IntentionAction, LowPriorityAction, Iconable {

    // This regex matches strings that start with a package name, consisting of one or more groups of a Java identifier followed by a period.
    // This is followed by an uppercase letter, which is the start of a class name,
    // and then one or more characters that can be part of a Java identifier, which make up the rest of the class name.
    //
    // Example: com.example.package.MyClass
    private val classnamePattern =
        Regex("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{Lu}\\p{javaJavaIdentifierPart}+")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        DependencyToolWindowOpener.activateToolWindow(project, PackagesListPanelProvider) {
            project.pkgsUiStateModifier.setSearchQuery(ref.canonicalText)
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = ref.element.run {
        // TODO PKGS-1047 Support JPS
        isValid && classnamePattern.matches(text) && project.packageSearchProjectService.isAvailable
    }

    override fun getText() = PackageSearchBundle.message("packagesearch.quickfix.packagesearch.action")

    @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name...
    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.packagesearch.family")

    override fun getIcon(flags: Int) = PackageSearchIcons.Package

    override fun startInWriteAction() = false
}
