// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.base.util.KOTLIN_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import java.util.function.Function
import javax.swing.JComponent

class JavaOutsideModuleDetector : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.extension != JavaFileType.DEFAULT_EXTENSION && !FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE)) {
            return null
        }

        if (ModuleUtilCore.findModuleForFile(file, project)?.isGradleModule != true) return null

        val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
        if (!fileIndex.isUnderSourceRootOfType(file, KOTLIN_SOURCE_ROOT_TYPES)) return null

        return Function {
            EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                text(KotlinJvmBundle.message("this.java.file.is.outside.of.java.source.roots.and.won.t.be.added.to.the.classpath"))
                icon(AllIcons.General.Warning)
            }
        }
    }
}