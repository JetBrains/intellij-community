// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.TestDataProvider
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.job
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.jvm.shared.scratch.getScratchEditorForSelectedFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchToolWindowFactory
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.assertEqualsToFile
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestDataFileName
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.Assert
import kotlin.io.path.Path
import kotlin.io.path.readText

abstract class AbstractK2ScratchRunActionTest : FileEditorManagerTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getTestDataPath() = TestMetadataUtil.getTestDataPath(this::class.java)

    fun doScratchTest(unused: String) {
        val fileName = getTestDataFileName(this::class.java, this.name) ?: error("scratch file not found")
        val editorWithPreview = configureScratchEditor(fileName)
        val scratchFile = editorWithPreview.scratchFile as? K2KotlinScratchFile ?: error("scratch file not found")

        launchAction(RunScratchActionK2())

        PlatformTestUtil.waitWhileBusy {
            scratchFile.executor.scope.coroutineContext.job.children.any { it.isActive }
        }

        UIUtil.dispatchAllInvocationEvents()

        val consoleView = ToolWindowManager.getInstance(project).getToolWindow(ScratchToolWindowFactory.ID)?.contentManager?.contents
            ?.firstNotNullOfOrNull { it.component as? ConsoleViewImpl } ?:error("failed to get console view")
        consoleView.flushDeferredText()

        val actualOutput = consoleView.editor?.document?.text ?: error("failed to get output text")
        val expectedOutputFile = Path(testDataPath, fileName.replace(".kts", ".output"))
        val scratchFilePath = editorWithPreview.scratchFile.virtualFile.path
        assertEqualsToFile(expectedOutputFile, actualOutput) { output ->
            output.replace(scratchFilePath, fileName)
                .replace(Regex("(Compilation failed: )(?:WARNING:[^\n]*\n|[ \t]*\n)+"), "$1")
                .replace(Regex("(?m)^WARNING:[^\n]*\n"), "")
        }

        val previewText = (editorWithPreview.previewEditor as? TextEditor)?.editor?.document?.text ?: error("failed to get explain text")
        val expectedExplainFile = Path(testDataPath, fileName.replace(".kts", ".explain"))
        assertEqualsToFile(expectedExplainFile, previewText)
    }

    protected fun configureScratchEditor(fileName: String): ScratchFileEditorWithPreview {
        val text = Path(testDataPath, fileName).readText()

        val scratchVirtualFile = ScratchRootType.getInstance().createScratchFile(
            project,
            fileName,
            KotlinLanguage.INSTANCE,
            text,
            ScratchFileService.Option.create_if_missing
        ) ?: error("Couldn't create scratch file")

        myFixture.openFileInEditor(scratchVirtualFile)

        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)

        val scratchFileEditor = manager?.let {
            getScratchEditorForSelectedFile(it, myFixture.file.virtualFile)
        } ?: error("Couldn't find scratch file")

        configureOptions(scratchFileEditor, text, myFixture.module)

        return scratchFileEditor
    }

    protected fun launchAction(action: AnAction) {
        val e = getActionEvent(action)
        ActionUtil.updateAction(action, e)
        Assert.assertTrue(e.presentation.isEnabledAndVisible)
        ActionUtil.performAction(action, e)
    }

    private fun getActionEvent(action: AnAction): AnActionEvent {
        val context = TestDataProvider(project)
        return TestActionEvent.createTestEvent(action, context::getData)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    companion object {
        fun configureOptions(
            scratchFileEditor: ScratchFileEditorWithPreview,
            fileText: String,
            module: Module?
        ) {
            val scratchFile = scratchFileEditor.scratchFile.apply {
                saveOptions { copy(isMakeBeforeRun = false) }
            }

            if (module != null && !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NO_MODULE")) {
                scratchFile.setModule(module)
            }

            val isPreviewEnabled = InTextDirectivesUtils.isDirectiveDefined(fileText, "// PREVIEW_ENABLED")
            scratchFileEditor.setPreviewEnabled(isPreviewEnabled)
        }
    }
}
