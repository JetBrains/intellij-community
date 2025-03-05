// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.TextAnnotationGutterProvider
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.BreakpointChecker.BreakpointType
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Color

class InspectBreakpointApplicabilityAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val data = e.getData() ?: return

        if (data.editor.gutter.isAnnotationsShown) {
            data.editor.gutter.closeAllAnnotations()
        }

        val checker = BreakpointChecker()

        val lineCount = data.file.getLineCount()
        val breakpoints = (0..lineCount).map { line -> checker.check(data.file, line) }
        val gutterProvider = BreakpointsGutterProvider(breakpoints)
        data.editor.gutter.registerTextAnnotation(gutterProvider)
    }

    private class BreakpointsGutterProvider(private val breakpoints: List<List<BreakpointType>>) : TextAnnotationGutterProvider {
        override fun getLineText(line: Int, editor: Editor?): String? {
            val breakpoints = breakpoints.getOrNull(line) ?: return null
            return breakpoints.joinToString { it.prefix }
        }

        override fun getToolTip(line: Int, editor: Editor?): String? = null
        override fun getStyle(line: Int, editor: Editor?) = EditorFontType.PLAIN

        override fun getPopupActions(line: Int, editor: Editor?) = emptyList<AnAction>()
        override fun getColor(line: Int, editor: Editor?): ColorKey? = null
        override fun getBgColor(line: Int, editor: Editor?): Color? = null
        override fun gutterClosed() {}
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = isApplicationInternalMode()
        e.presentation.isEnabled = e.getData() != null
    }

    class ActionData(val editor: Editor, val file: KtFile)

    private fun AnActionEvent.getData(): ActionData? {
        val editor = getData(CommonDataKeys.EDITOR) ?: return null
        val file = getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
        return ActionData(editor, file)
    }
}
