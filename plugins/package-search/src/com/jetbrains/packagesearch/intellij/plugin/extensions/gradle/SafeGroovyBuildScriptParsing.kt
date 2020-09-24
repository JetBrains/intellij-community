package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchVirtualFileAccess
import com.jetbrains.packagesearch.patchers.buildsystem.Gradle
import com.jetbrains.packagesearch.patchers.buildsystem.gradle.parser.groovy.GradleGroovyParserException

fun <T> parseGradleGroovyBuildScriptFrom(project: Project, virtualFile: VirtualFile, safeAction: (Gradle) -> T) =
    try {
        val fileAccess = PackageSearchVirtualFileAccess(project, virtualFile)
        safeAction(Gradle(fileAccess))
    } catch (e: GradleGroovyParserException) {
        throw RuntimeExceptionWithAttachments(e.message, e, Attachment("problematic-block", e.rawAttachedBlock))
    }
