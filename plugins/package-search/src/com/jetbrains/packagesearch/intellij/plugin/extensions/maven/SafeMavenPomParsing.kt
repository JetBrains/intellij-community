package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchVirtualFileAccess
import com.jetbrains.packagesearch.patchers.buildsystem.Maven
import com.jetbrains.packagesearch.patchers.buildsystem.maven.MavenParserException

fun <T> parseMavenPomFrom(project: Project, virtualFile: VirtualFile, safeAction: (Maven) -> T) =
    try {
        val fileAccess = PackageSearchVirtualFileAccess(project, virtualFile)
        safeAction(Maven(fileAccess))
    } catch (e: MavenParserException) {
        val attachment = try {
            val fileAccess = PackageSearchVirtualFileAccess(project, virtualFile)
            Attachment(virtualFile.name, fileAccess.loadText())
        } catch (ignored: RuntimeException) {
            val message = PackageSearchBundle.message("packagesearch.maven.error.cannotReadPomContentForReporting", virtualFile.name)
            @Suppress("TooGenericExceptionThrown") // This is the Exception Analyzer base exception
            throw RuntimeException(message, e)
        }
        throw RuntimeExceptionWithAttachments(e.message, e, attachment)
    }
