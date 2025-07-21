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
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.shared.getAllDefinitions
import org.jetbrains.kotlin.idea.core.script.shared.kotlinScriptTemplateInfo
import javax.swing.Icon
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

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

            val definitions = getAllDefinitions(project)
            if (definitions.isNotEmpty()) {
                definitions.mapNotNull {
                    it.compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptTemplateInfo]
                }.distinct().forEach {
                    builder.addKind(it.title, it.icon, it.templateName)
                }
            } else {
                builder
                    .addKind(KotlinScriptFileTemplate.GradleKts)
                    .addKind(KotlinScriptFileTemplate.MainKts)
                    .addKind(KotlinScriptFileTemplate.Kts)
            }

            setValidator(NewKotlinFileNameValidator)
        }
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        KotlinBundle.message("action.Kotlin.NewScript.text")
}

internal enum class KotlinScriptFileTemplate(@NlsContexts.ListItem override val title: String, override val icon: Icon, override val fileName: String): KotlinTemplate {
    GradleKts(".gradle.kts", KotlinIcons.GRADLE_SCRIPT, "Kotlin Script Gradle"),
    MainKts(".main.kts", KotlinIcons.SCRIPT, "Kotlin Script MainKts"),
    Kts(".kts", KotlinIcons.SCRIPT, "Kotlin Script"),
}