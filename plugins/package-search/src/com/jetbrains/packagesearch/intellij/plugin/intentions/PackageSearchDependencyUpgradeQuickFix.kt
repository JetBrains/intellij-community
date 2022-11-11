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

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.NotifyingOperationExecutor

internal class PackageSearchDependencyUpgradeQuickFix(
    element: PsiElement,
    private val identifier: PackageIdentifier,
    @SafeFieldForPreview private val targetVersion: PackageVersion.Named,
    @SafeFieldForPreview private val operations: List<PackageSearchOperation<*>>
) : LocalQuickFixAndIntentionActionOnPsiElement(element), HighPriorityAction {

    override fun getFamilyName() = PackageSearchBundle.message("packagesearch.quickfix.upgrade.family")

    override fun getText() = PackageSearchBundle.message(
        "packagesearch.quickfix.upgrade.action",
        identifier.rawValue,
        targetVersion
    )

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        NotifyingOperationExecutor(project).executeOperations(operations)
    }
}
