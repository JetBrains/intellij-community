// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.internal.KotlinBytecodeToolWindow

class ShowKotlinBytecodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindowManager = ToolWindowManager.getInstance(project)

        val toolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: toolWindowManager.registerToolWindow(
            TOOLWINDOW_ID,
            false,
            ToolWindowAnchor.RIGHT,
        )
            .apply {
                setIcon(KotlinIcons.SMALL_LOGO_13)
                val contentFactory = ContentFactory.SERVICE.getInstance()
                contentManager.addContent(contentFactory.createContent(KotlinBytecodeToolWindow(project, this), "", false))
            }

        toolWindow.activate(null)
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabled = e.project != null && file?.fileType == KotlinFileType.INSTANCE
    }

    companion object {
        const val TOOLWINDOW_ID = "Kotlin Bytecode"
    }
}
