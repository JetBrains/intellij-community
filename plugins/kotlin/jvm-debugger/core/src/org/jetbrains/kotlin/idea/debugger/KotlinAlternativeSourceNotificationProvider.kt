// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.AlternativeSourceNotificationProvider
import com.intellij.ide.util.ModuleRendererFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotificationProvider.CONST_NULL
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil.findFilesWithExactPackage
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent

class KotlinAlternativeSourceNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
        if (!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE) {
            return CONST_NULL
        }

        val javaSession = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
        val session = javaSession?.xDebugSession
        if (session == null) {
            AlternativeSourceNotificationProvider.setFileProcessed(file, false)
            return CONST_NULL
        }

        val position = session.currentPosition
        if (file != position?.file) {
            AlternativeSourceNotificationProvider.setFileProcessed(file, false)
            return CONST_NULL
        }

        if (DumbService.getInstance(project).isDumb) return CONST_NULL

        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return CONST_NULL
        val alternativeKtFiles = findAlternativeKtFiles(ktFile, project, javaSession)

        AlternativeSourceNotificationProvider.setFileProcessed(file, true)

        if (alternativeKtFiles.size <= 1) {
            return CONST_NULL
        }

        val currentFirstAlternatives: Collection<KtFile> = listOf(ktFile) + alternativeKtFiles.filter { it != ktFile }

        val locationDeclName: String? = when (val frame = session.currentStackFrame) {
            is JavaStackFrame -> {
                val location = frame.descriptor.location
                location?.declaringType()?.name()
            }
            else -> null
        }

        return Function {
            AlternativeSourceNotificationPanel(it, project, currentFirstAlternatives, file, locationDeclName)
        }
    }

    private class AlternativeSourceNotificationPanel(
        fileEditor: FileEditor,
        project: Project,
        alternatives: Collection<KtFile>,
        file: VirtualFile,
        locationDeclName: String?,
    ) : EditorNotificationPanel(fileEditor) {
        private class ComboBoxFileElement(val ktFile: KtFile) {
            private val label: String by lazy(LazyThreadSafetyMode.NONE) {
                val factory = ModuleRendererFactory.findInstance(ktFile)
                factory.getModuleTextWithIcon(ktFile)?.text ?: ""
            }

            override fun toString(): String = label
        }

        init {
            text = KotlinDebuggerCoreBundle.message("alternative.sources.notification.title", file.name)

            val items = alternatives.map { ComboBoxFileElement(it) }
            myLinksPanel.add(
                ComboBox(items.toTypedArray()).apply {
                    addActionListener {
                        val context = DebuggerManagerEx.getInstanceEx(project).context
                        val session = context.debuggerSession
                        val ktFile = (selectedItem as ComboBoxFileElement).ktFile
                        val vFile = ktFile.containingFile.virtualFile

                        when {
                            session != null && vFile != null ->
                                session.process.managerThread.schedule(
                                    object : DebuggerCommandImpl() {
                                        override fun action() {
                                            if (!StringUtil.isEmpty(locationDeclName)) {
                                                DebuggerUtilsEx.setAlternativeSourceUrl(locationDeclName, vFile.url, project)
                                            }

                                            DebuggerUIUtil.invokeLater {
                                                FileEditorManager.getInstance(project).closeFile(file)
                                                session.refresh(true)
                                            }
                                        }
                                    },
                                )
                            else -> {
                                FileEditorManager.getInstance(project).closeFile(file)
                                ktFile.navigate(true)
                            }
                        }
                    }
                },
            )

            createActionLabel(KotlinDebuggerCoreBundle.message("alternative.sources.notification.hide")) {
                DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE = false
                AlternativeSourceNotificationProvider.setFileProcessed(file, false)
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.getSelectedEditor(file)
                if (editor != null) {
                    fileEditorManager.removeTopComponent(editor, this)
                }
            }
        }
    }
}

private fun findAlternativeKtFiles(ktFile: KtFile, project: Project, javaSession: DebuggerSession): Set<KtFile> {
    val packageFqName = ktFile.packageFqName
    val fileName = ktFile.name
    val platform = ktFile.platform
    return findFilesWithExactPackage(
        packageFqName,
        javaSession.searchScope,
        project,
    ).filterTo(HashSet()) {
        it.name == fileName && it.platformMatches(platform)
    }
}

private fun KtFile.platformMatches(otherPlatform: TargetPlatform): Boolean =
    when {
        platform.isJvm()    -> otherPlatform.isJvm()
        platform.isJs()     -> otherPlatform.isJs()
        platform.isNative() -> otherPlatform.isNative()
        platform.isCommon() -> otherPlatform.isCommon()
        else -> true
    }
