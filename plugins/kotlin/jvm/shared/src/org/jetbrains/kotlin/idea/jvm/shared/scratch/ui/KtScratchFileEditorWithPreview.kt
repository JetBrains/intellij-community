// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.shared.scratch.ui

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.*
import org.jetbrains.kotlin.psi.UserDataProperty

abstract class KtScratchFileEditorWithPreview(
    kotlinScratchFile: ScratchFile, sourceTextEditor: TextEditor, previewTextEditor: TextEditor
) : TextEditorWithPreview(sourceTextEditor, previewTextEditor), TextEditor, ScratchEditorLinesTranslator {

    val scratchFile: ScratchFile = kotlinScratchFile

    private val sourceEditor = sourceTextEditor.editor as EditorEx
    private val _previewEditor = previewTextEditor.editor as EditorEx
    private val previewOutputManager: PreviewOutputBlocksManager = PreviewOutputBlocksManager(_previewEditor)

    protected val toolWindowHandler: ScratchOutputHandler = requestToolWindowHandler()
    private val inlayScratchOutputHandler = InlayScratchOutputHandler(sourceTextEditor, toolWindowHandler)
    private val previewEditorScratchOutputHandler = PreviewEditorScratchOutputHandler(
      previewOutputManager, toolWindowHandler, previewTextEditor as Disposable
    )
    protected val commonPreviewOutputHandler: LayoutDependantOutputHandler = LayoutDependantOutputHandler(
        noPreviewOutputHandler = inlayScratchOutputHandler,
        previewOutputHandler = previewEditorScratchOutputHandler,
        layoutProvider = { getLayout()!! })

    init {
        sourceTextEditor.parentScratchEditorWithPreview = this
        previewTextEditor.parentScratchEditorWithPreview = this

        configureSyncScrollForSourceAndPreview()
        configureSyncHighlighting(sourceEditor, _previewEditor, translator = this)

        ScratchFileAutoRunner.addListener(kotlinScratchFile.project, sourceTextEditor)
    }

    override fun getFile(): VirtualFile = scratchFile.file

    override fun previewLineToSourceLines(previewLine: Int): Pair<Int, Int>? {
        val expressionUnderCaret = scratchFile.getExpressionAtLine(previewLine) ?: return null
        val outputBlock = previewOutputManager.getBlock(expressionUnderCaret) ?: return null

        return outputBlock.lineStart to outputBlock.lineEnd
    }

    override fun sourceLineToPreviewLines(sourceLine: Int): Pair<Int, Int>? {
        val block = previewOutputManager.getBlockAtLine(sourceLine) ?: return null
        if (!block.sourceExpression.linesInformationIsCorrect()) return null

        return block.sourceExpression.lineStart to block.sourceExpression.lineEnd
    }

    private fun configureSyncScrollForSourceAndPreview() {
        val scrollable = object : BaseSyncScrollable() {
            override fun processHelper(helper: ScrollHelper) {
                if (!helper.process(0, 0)) return

                val alignments = previewOutputManager.computeSourceToPreviewAlignments()

                for ((fromSource, fromPreview) in alignments) {
                    if (!helper.process(fromSource, fromPreview)) return
                    if (!helper.process(fromSource, fromPreview)) return
                }

                helper.process(sourceEditor.document.lineCount, _previewEditor.document.lineCount)
            }

            override fun isSyncScrollEnabled(): Boolean = true
        }

        val scrollSupport = SyncScrollSupport.TwosideSyncScrollSupport(listOf(sourceEditor, _previewEditor), scrollable)
        val listener = VisibleAreaListener { e -> scrollSupport.visibleAreaChanged(e) }

        sourceEditor.scrollingModel.addVisibleAreaListener(listener)
        _previewEditor.scrollingModel.addVisibleAreaListener(listener)
    }

    override fun dispose() {
        //scratchFile.replScratchExecutor?.stop()
        //scratchFile.compilingScratchExecutor?.stop()
      releaseToolWindowHandler(toolWindowHandler)
        super.dispose()
    }

    override fun navigateTo(navigatable: Navigatable) {
        myEditor.navigateTo(navigatable)
    }

    override fun canNavigateTo(navigatable: Navigatable): Boolean {
        return myEditor.canNavigateTo(navigatable)
    }

    override fun getEditor(): Editor {
        return myEditor.editor
    }

    fun clearOutputHandlers() {
        commonPreviewOutputHandler.clear(scratchFile)
    }

    override val isShowActionsInTabs: Boolean
        get() = false

    override fun createViewActionGroup(): ActionGroup {
        return DefaultActionGroup(showEditorAction, showEditorAndPreviewAction)
    }

    /**
     * For simple actions, [com.intellij.openapi.actionSystem.Presentation.getText] is shown in the tooltip in the [ActionToolbar], and [com.intellij.openapi.actionSystem.Presentation.getDescription] is shown
     * in the bottom tool panel. But when action implements [com.intellij.openapi.actionSystem.ex.CustomComponentAction], its tooltip is
     * controlled only by its [javax.swing.JComponent.setToolTipText] method.
     *
     * That's why we set long and descriptive [com.intellij.openapi.actionSystem.Presentation.getText], but short [com.intellij.openapi.actionSystem.Presentation.getDescription].
     */

    override val showEditorAction: ToggleAction
        get() {
            return super.showEditorAction.apply {
                templatePresentation.text = KotlinJvmBundle.message("scratch.inlay.output.mode.title")
                templatePresentation.description = KotlinJvmBundle.message("scratch.inlay.output.mode.description")
            }
        }

    override val showEditorAndPreviewAction: ToggleAction
        get() {
            return super.showEditorAndPreviewAction.apply {
                templatePresentation.text = KotlinJvmBundle.message("scratch.side.panel.output.mode.title")
                templatePresentation.description = KotlinJvmBundle.message("scratch.side.panel.output.mode.description")
            }
        }

    override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {
        when {
            oldValue != newValue -> clearOutputHandlers()
        }
    }

    @TestOnly
    fun setPreviewEnabled(isPreviewEnabled: Boolean) {
        setLayout(if (isPreviewEnabled) Layout.SHOW_EDITOR_AND_PREVIEW else Layout.SHOW_EDITOR)
    }
}

fun TextEditor.findScratchFileEditorWithPreview(): KtScratchFileEditorWithPreview? =
    this as? KtScratchFileEditorWithPreview ?: parentScratchEditorWithPreview

var TextEditor.parentScratchEditorWithPreview: KtScratchFileEditorWithPreview? by UserDataProperty(Key.create("parent.preview.editor"))


/**
 * Redirects output to [noPreviewOutputHandler] or [previewOutputHandler] depending on the result of [layoutProvider] call.
 *
 * However, clears both handlers to simplify clearing when switching between layouts.
 */
class LayoutDependantOutputHandler(
  private val noPreviewOutputHandler: ScratchOutputHandler,
  private val previewOutputHandler: ScratchOutputHandler,
  private val layoutProvider: () -> TextEditorWithPreview.Layout
) : ScratchOutputHandler {

    override fun onStart(file: ScratchFile) {
        targetHandler.onStart(file)
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        targetHandler.handle(file, expression, output)
    }

    override fun error(file: ScratchFile, message: String) {
        targetHandler.error(file, message)
    }

    override fun handle(file: ScratchFile, infos: List<ExplainInfo>, scope: CoroutineScope) {
        targetHandler.handle(file, infos, scope)
    }

    override fun onFinish(file: ScratchFile) {
        targetHandler.onFinish(file)
    }

    override fun clear(file: ScratchFile) {
        noPreviewOutputHandler.clear(file)
        previewOutputHandler.clear(file)
    }

    private val targetHandler
        get() = when (layoutProvider()) {
            TextEditorWithPreview.Layout.SHOW_EDITOR -> noPreviewOutputHandler
            else -> previewOutputHandler
        }
}

/**
 * Checks if [ScratchExpression.element] is actually starts at the [ScratchExpression.lineStart]
 * and ends at the [ScratchExpression.lineEnd].
 */
fun ScratchExpression.linesInformationIsCorrect(): Boolean {
    if (!element.isValid) return false
    return element.getLineNumber(start = true) == lineStart && element.getLineNumber(start = false) == lineEnd
}
