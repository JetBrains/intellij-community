// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.AlternativeSourceNotificationPanel
import com.intellij.debugger.ui.AlternativeSourceNotificationPanel.AlternativeSourceElement
import com.intellij.debugger.ui.AlternativeSourceNotificationProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils.findFilesWithExactPackage
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinAllFilesScopeProvider
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent

class KotlinAlternativeSourceNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE) {
            return null
        }

        val javaSession = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
        val session = javaSession?.xDebugSession
        if (session == null) {
            AlternativeSourceNotificationProvider.setFileProcessed(file, false)
            return null
        }

        val position = session.currentPosition
        if (file != position?.file) {
            AlternativeSourceNotificationProvider.setFileProcessed(file, false)
            return null
        }

        if (DumbService.getInstance(project).isDumb) return null

        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        val alternativeKtFiles = findAlternativeKtFiles(ktFile, project, javaSession)

        AlternativeSourceNotificationProvider.setFileProcessed(file, true)

        if (alternativeKtFiles.size <= 1) {
            return null
        }

        val alternatives = listOf(ktFile) + alternativeKtFiles.filter { it != ktFile }
        val items = alternatives.map { AlternativeSourceElement(it) }

        val locationDeclName: String? = when (val frame = session.currentStackFrame) {
            is JavaStackFrame -> {
                val location = frame.descriptor.location
                location?.declaringType()?.name()
            }
            else -> null
        }

        return Function {
            AlternativeSourceNotificationPanel(
                it, project, KotlinDebuggerCoreBundle.message("alternative.sources.notification.title", file.name),
                file, items.toTypedArray(), locationDeclName
            )
        }
    }
}

private fun findAlternativeKtFiles(ktFile: KtFile, project: Project, javaSession: DebuggerSession): Set<KtFile> {
    val packageFqName = ktFile.packageFqName
    val fileName = ktFile.name
    val platform = ktFile.platform
    val allFilesSearchScope = KotlinAllFilesScopeProvider.getInstance(project).getAllKotlinFilesScope()

    fun matches(file: KtFile): Boolean =
        file.name == fileName && file.platformMatches(platform)

    val result = HashSet<KtFile>()
    findFilesWithExactPackage(packageFqName, javaSession.searchScope, project).filterTo(result, ::matches)
    findFilesWithExactPackage(packageFqName, allFilesSearchScope, project).filterTo(result, ::matches)

    return result
}

private fun KtFile.platformMatches(otherPlatform: TargetPlatform): Boolean =
    when {
        platform.isJvm()    -> otherPlatform.isJvm()
        platform.isJs()     -> otherPlatform.isJs()
        platform.isNative() -> otherPlatform.isNative()
        platform.isCommon() -> otherPlatform.isCommon()
        else -> true
    }
