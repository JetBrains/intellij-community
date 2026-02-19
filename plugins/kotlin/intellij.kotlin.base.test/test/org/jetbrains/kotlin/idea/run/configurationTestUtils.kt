// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryFactory

fun getConfigurations(file: VirtualFile, project: Project, pattern: String): List<ConfigurationFromContext> {
    val location: PsiLocation<PsiElement?> =
        when {
            file.isFile -> {
                val psiFile = PsiManager.getInstance(project).findFile(file) ?: error("PsiFile not found for $file")
                val offset = psiFile.text.indexOf(pattern)
                val psiElement = psiFile.findElementAt(offset)
                PsiLocation(psiElement)
            }
            file.isDirectory -> {
                val directory = PsiDirectoryFactory.getInstance(project).createDirectory(file)
                PsiLocation(directory)
            }
            else -> {
                error("")
            }
        }
    val context = ConfigurationContext.createEmptyContextForLocation(location)
    return context.configurationsFromContext.orEmpty()
}

fun getConfiguration(file: VirtualFile, project: Project, pattern: String): ConfigurationFromContext =
    getConfigurations(file, project, pattern).singleOrNull() ?: error("Configuration not found for pattern $pattern")