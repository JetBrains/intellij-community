// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.fileTemplates.DefaultCreateFromTemplateHandler
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded

class KotlinCreateFromTemplateHandler : DefaultCreateFromTemplateHandler() {
    override fun handlesTemplate(template: FileTemplate): Boolean = template.isTemplateOfType(KotlinFileType.INSTANCE)

    override fun prepareProperties(props: MutableMap<String, Any>) {
        val packageName = props[FileTemplate.ATTRIBUTE_PACKAGE_NAME] as? String
        if (!packageName.isNullOrEmpty()) {
            props[FileTemplate.ATTRIBUTE_PACKAGE_NAME] = packageName.split('.').joinToString(".", transform = String::quoteIfNeeded)
        }

        val name = props[FileTemplate.ATTRIBUTE_NAME] as? String
        if (name != null) {
            props[FileTemplate.ATTRIBUTE_NAME] = name.quoteIfNeeded()
        }
    }

    override fun createFromTemplate(
        project: Project,
        directory: PsiDirectory,
        fileName: String?,
        template: FileTemplate,
        templateText: String,
        props: Map<String?, Any?>
    ): PsiElement {
        return super.createFromTemplate(project, directory, fileName, template, templateText, props)
    }
}