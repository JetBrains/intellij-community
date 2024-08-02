// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.actions.CreateTemplateInPackageAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import kotlin.collections.none

internal class NewKotlinScriptAction : AbstractNewKotlinFileAction(), DumbAware {
    override fun isAvailable(dataContext: DataContext): Boolean {
        if (!super.isAvailable(dataContext)) return false

        val ideView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex

        return ideView.directories.none {
            projectFileIndex.isInSourceContent(it.virtualFile) ||
                    CreateTemplateInPackageAction.isInContentRoot(it.virtualFile, projectFileIndex)
        }
    }

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        with(builder) {
            setTitle(KotlinBundle.message("action.new.script.dialog.title"))
            addKind(KotlinFileTemplate.Script)
            setValidator(NewKotlinFileNameValidator)
        }
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        KotlinBundle.message("action.Kotlin.NewScript.text")
}