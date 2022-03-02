// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotificationProvider.CONST_NULL
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.configuration.isGradleModule
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.util.findModule
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.util.function.Function
import javax.swing.JComponent

class JavaOutsideModuleDetector : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
        if (file.extension != JavaFileType.DEFAULT_EXTENSION && !FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE)) {
            return CONST_NULL
        }
        val module = file.findModule(project)?.takeIf(Module::isGradleModule) ?: return CONST_NULL
        val facetSettings = KotlinFacet.get(module)?.configuration?.settings ?: return CONST_NULL

        val filePath = file.path
        val nonKotlinPath = module.sourceRoots.map { it.path } - facetSettings.pureKotlinSourceFolders
        if (nonKotlinPath.any { filePath.startsWith(it) }) return CONST_NULL

        return Function {
            EditorNotificationPanel(it).apply {
                text(KotlinJvmBundle.message("this.java.file.is.outside.of.java.source.roots.and.won.t.be.added.to.the.classpath"))
                icon(AllIcons.General.Warning)
            }
        }
    }
}