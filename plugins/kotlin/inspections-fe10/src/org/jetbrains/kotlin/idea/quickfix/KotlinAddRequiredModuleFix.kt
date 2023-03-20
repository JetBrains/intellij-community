// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.idea.configuration.AddRequiresDirectiveFacility
import org.jetbrains.kotlin.idea.fe10.inspections.KotlinInspectionsFe10Bundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm

class KotlinAddRequiredModuleFix(module: PsiJavaModule, private val requiredName: String) :
    LocalQuickFixAndIntentionActionOnPsiElement(module) {
    override fun getFamilyName(): String = KotlinInspectionsFe10Bundle.message("kotlin.add.required.module.fix.family.name")

    override fun getText(): String = QuickFixBundle.message("module.info.add.requires.name", requiredName)

    override fun startInWriteAction() = true

    override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
        return PsiUtil.isLanguageLevel9OrHigher(file) &&
                startElement is PsiJavaModule &&
                startElement.getManager().isInProject(startElement) &&
                AddRequiresDirectiveFacility.canAddModuleRequirement(startElement)
    }

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        AddRequiresDirectiveFacility.addModuleRequirement(startElement as PsiJavaModule, requiredName)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val expression = diagnostic.psiElement as? KtExpression ?: return null
            val javaModule = JavaModuleGraphUtil.findDescriptorByElement(expression) ?: return null

            val dependDiagnostic = DiagnosticFactory.cast(diagnostic, ErrorsJvm.JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE)
            val moduleName = dependDiagnostic.a

            return KotlinAddRequiredModuleFix(javaModule, moduleName)
        }
    }
}