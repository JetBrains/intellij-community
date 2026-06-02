// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.jvm.shared.scratch.isKotlinScratch
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchToolWindowHandlerKeeper
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorProvider
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ScratchFileEditorWithPreview

internal class K2ScratchFileEditorProvider : KtScratchFileEditorProvider() {
    override fun accept(project: Project, file: VirtualFile): Boolean = file.isValid && file.isKotlinScratch

    override suspend fun createFileEditor(
        project: Project, file: VirtualFile, document: Document?, editorCoroutineScope: CoroutineScope
    ): FileEditor {
        val textEditorProvider = TextEditorProvider.getInstance()

        val scratchFile =
            K2KotlinScratchFile(project, file, editorCoroutineScope.childScope(K2KotlinScratchFile::class.java.simpleName))

        val mainEditor = textEditorProvider.createFileEditor(
            project = project,
            file = scratchFile.virtualFile,
            document = readAction { FileDocumentManager.getInstance().getDocument(scratchFile.virtualFile) },
            editorCoroutineScope = editorCoroutineScope,
        )

        val editorFactory = serviceAsync<EditorFactory>()

        return withContext(Dispatchers.EDT) {
            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })
            val previewEditor = textEditorProvider.getTextEditor(viewer)
            K2ScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }

    override fun createEditor(
        project: Project,
        file: VirtualFile
    ): FileEditor {
        TODO("suspend createFileEditor should be used")
    }
}

class K2ScratchFileEditorWithPreview(
    val kotlinScratchFile: K2KotlinScratchFile, sourceTextEditor: TextEditor, previewTextEditor: TextEditor
) : ScratchFileEditorWithPreview(
    kotlinScratchFile,
    sourceTextEditor,
    previewTextEditor,
    initialLayout = if (kotlinScratchFile.options.isExplainEnabled) {
        Layout.SHOW_EDITOR_AND_PREVIEW
    } else {
        Layout.SHOW_EDITOR
    },
) {

    init {
        kotlinScratchFile.executor.addOutputHandler(previewEditorScratchOutputHandler)
    }

    override fun dispose() {
        kotlinScratchFile.executor.stop()
        ScratchToolWindowHandlerKeeper.releaseOutputHandler(toolWindowHandler)
        super.dispose()
    }

    override fun createToolbar(): ActionToolbar = ScratchTopPanelK2(kotlinScratchFile).actionsToolbar

    override fun createViewActionGroup(): ActionGroup = DefaultActionGroup(
        MakeBeforeRunToggleAction(),
        InteractiveModeToggleAction(),
        ExplainCodeModeToggleAction(),
    )

    @TestOnly
    fun getViewActionsForTesting(): List<com.intellij.openapi.actionSystem.AnAction> =
        createViewActionGroup().getChildren(null).toList()

    override fun requestOutputHandler(): ScratchOutputHandler = ScratchToolWindowHandlerKeeper.requestOutputHandler()

    private inner class MakeBeforeRunToggleAction : ToggleAction() {
        init {
            templatePresentation.text = KotlinJvmBundle.message("scratch.toggle.make.before.run.text")
            templatePresentation.description = KotlinJvmBundle.message("scratch.toggle.make.before.run.description")
            templatePresentation.icon = AllIcons.Actions.Compile
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return kotlinScratchFile.options.isMakeBeforeRun
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (kotlinScratchFile.module == null || kotlinScratchFile.options.isInteractiveMode) return
            kotlinScratchFile.saveOptions { copy(isMakeBeforeRun = state) }
            ActivityTracker.getInstance().inc()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            val selectedModule = kotlinScratchFile.module?.takeIf { !it.isDisposed }
            val isInteractiveMode = kotlinScratchFile.options.isInteractiveMode
            e.presentation.icon = AllIcons.Actions.Compile
            e.presentation.isVisible = selectedModule != null
            e.presentation.isEnabled = selectedModule != null && !isInteractiveMode
            val disabledByInteractiveModeMessage = KotlinJvmBundle.message("scratch.toggle.make.before.run.interactive.disabled.description")
            e.presentation.text = if (isInteractiveMode) disabledByInteractiveModeMessage else templatePresentation.text
            e.presentation.description = when {
                isInteractiveMode -> disabledByInteractiveModeMessage
                selectedModule != null -> KotlinJvmBundle.message("scratch.toggle.make.before.run.module.description", selectedModule.name)
                else -> KotlinJvmBundle.message("scratch.toggle.make.before.run.disabled.description")
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class InteractiveModeToggleAction : ToggleAction() {
        init {
            templatePresentation.text = KotlinJvmBundle.message("scratch.toggle.interactive.mode.text")
            templatePresentation.description = KotlinJvmBundle.message(
                "scratch.toggle.interactive.mode.description",
                ScratchFileAutoRunner.AUTO_RUN_DELAY_MS / 1000,
            )
            templatePresentation.icon = AllIcons.Actions.Lightning
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return kotlinScratchFile.options.isInteractiveMode
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            kotlinScratchFile.saveOptions { copy(isInteractiveMode = state) }
            ActivityTracker.getInstance().inc()
            if (state) {
                (ScratchFileAutoRunner.getInstance(kotlinScratchFile.project) as? ScratchFileAutoRunnerK2)?.submitRun(kotlinScratchFile)
            }
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.icon = AllIcons.Actions.Lightning
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ExplainCodeModeToggleAction : ToggleAction() {
        init {
            templatePresentation.text = KotlinJvmBundle.message("scratch.toggle.explain.mode.text")
            templatePresentation.description = KotlinJvmBundle.message("scratch.toggle.explain.mode.description")
            templatePresentation.icon = AllIcons.General.InspectionsEye
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return kotlinScratchFile.options.isExplainEnabled
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            setExplainModeEnabled(state)
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.icon = AllIcons.General.InspectionsEye
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}
