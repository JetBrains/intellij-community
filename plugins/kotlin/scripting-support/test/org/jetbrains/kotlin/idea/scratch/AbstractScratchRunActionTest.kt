// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.actions.KOTLIN_WORKSHEET_EXTENSION
import org.jetbrains.kotlin.idea.base.highlighting.shouldHighlightFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.jvm.k1.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.jvm.shared.scratch.getScratchEditorForSelectedFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.InlayScratchFileRenderer
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import org.jetbrains.kotlin.parsing.KotlinParserDefinition.Companion.STD_SCRIPT_SUFFIX
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.junit.Assert
import java.io.File
import java.util.concurrent.TimeUnit

abstract class AbstractScratchRunActionTest : FileEditorManagerTestCase(),
                                              ExpectedPluginModeProvider {

    private val scratchFiles: MutableList<VirtualFile> = ArrayList()
    private var vfsDisposable: Ref<Disposable>? = null

    protected open fun fileName(): String = getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    override fun getTestDataPath() = TestMetadataUtil.getTestDataPath(this::class.java)

    fun doRightPreviewPanelOutputTest(unused: String) {
        doRightPreviewPanelOutputTest(isRepl = false)
    }

    fun doWorksheetReplTest(unused: String) {
        doInlayOutputTest(isRepl = true, isWorksheet = true)
    }

    fun doScratchReplTest(unused: String) {
        doInlayOutputTest(isRepl = true, isWorksheet = false)
    }

    fun doWorksheetCompilingTest(unused: String) {
        doInlayOutputTest(isRepl = false, isWorksheet = true)
    }

    fun doScratchCompilingTest(unused: String) {
        doInlayOutputTest(isRepl = false, isWorksheet = false)
    }

    fun doWorksheetMultiFileTest(unused: String) {
        doMultiFileTest(fileName(), isWorksheet = true)
    }

    fun doScratchMultiFileTest(unused: String) {
        doMultiFileTest(fileName(), isWorksheet = false)
    }

    private fun doMultiFileTest(dirName: String, isWorksheet: Boolean) {
        val mainFileExtension = if (isWorksheet) KOTLIN_WORKSHEET_EXTENSION else STD_SCRIPT_SUFFIX

        val javaFiles = arrayListOf<File>()
        val kotlinFiles = arrayListOf<File>()
        val kotlinScriptFiles = arrayListOf<File>()
        val baseDir = File(testDataPath, dirName)
        baseDir.walk().forEach {
            if (it.isFile) {
                when (it.extension) {
                    "java" -> javaFiles.add(it)
                    "kt" -> kotlinFiles.add(it)
                    "kts" -> kotlinScriptFiles.add(it)
                }
            }
        }

        val options = mutableListOf<String>()
        val testDataPathFile = File(myFixture.testDataPath)
        javaFiles.forEach {
            myFixture.copyFileToProject(
                FileUtil.getRelativePath(testDataPathFile, it)!!,
                FileUtil.getRelativePath(baseDir, it)!!
            )
        }
        kotlinFiles.forEach {
            myFixture.copyFileToProject(
                FileUtil.getRelativePath(testDataPathFile, it)!!,
                FileUtil.getRelativePath(baseDir, it)!!
            )
        }

        (kotlinFiles + kotlinScriptFiles).forEach {
            val fileText = FileUtil.loadFile(it, true)
            InTextDirectivesUtils.findListWithPrefixes(
                fileText, "// ${CompilerTestDirectives.COMPILER_ARGUMENTS_DIRECTIVE} "
            ).firstOrNull()?.let { options += it }
        }

        options.addAll(listOf("-language-version", "1.9"))

        val outputDir = FileUtil.createTempDirectory(dirName, "")

        KotlinCompilerStandalone(
            listOf(baseDir),
            target = outputDir,
            classpath = listOf(
                TestKotlinArtifacts.kotlinScriptRuntime,
                TestKotlinArtifacts.jetbrainsAnnotations
            ),
            options = options
        ).compile()

        PsiTestUtil.setCompilerOutputPath(myFixture.module, outputDir.path, false)

        val mainFileName = "$dirName/${getTestName(true)}.$mainFileExtension"
        doInlayOutputTest(mainFileName, isRepl = false, isWorksheet = isWorksheet)

        launchAction(ClearScratchAction())

        doInlayOutputTest(mainFileName, isRepl = true, isWorksheet = isWorksheet)

        ModuleRootModificationUtil.updateModel(myFixture.module) { model ->
            model.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(true)
        }
    }

    private fun doInlayOutputTest(fileName: String = fileName(), isRepl: Boolean, isWorksheet: Boolean) {
        configureAndLaunchScratch(fileName = fileName, isRepl = isRepl, isWorksheet = isWorksheet)

        val actualOutput = getFileTextWithInlays()

        val expectedFile = getExpectedFile(fileName, isRepl, suffix = "after")
        assertEqualsToFile(expectedFile, actualOutput)
    }

    private fun doRightPreviewPanelOutputTest(isRepl: Boolean) {
        val fileName = fileName()
        configureAndLaunchScratch(fileName, isRepl = isRepl, isWorksheet = false)

        val previewTextWithFoldings = getPreviewTextWithFoldings()

        val expectedFile = getExpectedFile(fileName, isRepl, suffix = "preview")
        assertEqualsToFile(expectedFile, previewTextWithFoldings)
    }

    private fun configureAndLaunchScratch(fileName: String, isRepl: Boolean, isWorksheet: Boolean) {
        val sourceFile = File(testDataPath, fileName)
        val fileText = sourceFile.readText().inlinePropertiesValues(isRepl)

        if (isWorksheet) {
            configureWorksheetByText(sourceFile.name, fileText)
        } else {
            configureScratchByText(sourceFile.name, fileText)
        }

        val containingFile = myFixture.file
        if (containingFile !is KtFile || !containingFile.shouldHighlightFile()) {
            error("Highlighting for scratch file is switched off")
        }

        launchScratch()
        waitUntilScratchFinishes(isRepl)
    }

    private fun getExpectedFile(fileName: String, isRepl: Boolean, suffix: String): File {
        val expectedFileName = if (isRepl) {
            fileName.replace(".kts", ".repl.$suffix")
        } else {
            fileName.replace(".kts", ".comp.$suffix")
        }

        return File(testDataPath, expectedFileName)
    }

    protected fun String.inlinePropertiesValues(
        isRepl: Boolean = false,
        isInteractiveMode: Boolean = false
    ): String {
        return replace("~REPL_MODE~", isRepl.toString()).replace("~INTERACTIVE_MODE~", isInteractiveMode.toString())
    }

    protected fun getFileTextWithInlays(): String {
        val doc = myFixture.getDocument(myFixture.file) ?: error("Document for ${myFixture.file.name} is null")
        val actualOutput = StringBuilder(myFixture.file.text)
        for (line in doc.lineCount - 1 downTo 0) {
            getInlays(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
                .forEach { inlay ->
                    val str = inlay.toString()
                    val offset = doc.getLineEndOffset(line)
                    actualOutput.insert(
                        offset,
                        "${str.takeWhile { it.isWhitespace() }}// ${str.trim()}"
                    )
                }
        }
        return actualOutput.toString().trim()
    }

    private fun getPreviewTextWithFoldings(): String {
        val scratchFileEditor = getScratchEditorForSelectedFile(manager!!, myFixture.file.virtualFile)
                                ?: error("Couldn't find scratch panel")

        val previewEditor = scratchFileEditor.previewEditor as TextEditor
        return getFoldingData(previewEditor.editor, withCollapseStatus = false)
    }

    protected fun getInlays(start: Int = 0, end: Int = myFixture.file.textLength): List<InlayScratchFileRenderer> {
        val inlineElementsInRange = myFixture.editor.inlayModel
            .getAfterLineEndElementsInRange(start, end)
            .filter { it.renderer is InlayScratchFileRenderer }
        return inlineElementsInRange.map { it.renderer as InlayScratchFileRenderer }
    }

    protected fun configureScratchByText(name: String, text: String): ScratchFile {
        val scratchVirtualFile = ScratchRootType.getInstance().createScratchFile(
            project,
            name,
            KotlinLanguage.INSTANCE,
            text,
            ScratchFileService.Option.create_if_missing
        ) ?: error("Couldn't create scratch file")
        scratchFiles.add(scratchVirtualFile)

        myFixture.openFileInEditor(scratchVirtualFile)

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)

        val scratchFileEditor = getScratchEditorForSelectedFile(manager!!, myFixture.file.virtualFile)
                                ?: error("Couldn't find scratch file")

        configureOptions(scratchFileEditor, text, myFixture.module)

        return scratchFileEditor.scratchFile
    }

    protected fun configureWorksheetByText(name: String, text: String): ScratchFile {
        val worksheetFile = myFixture.configureByText(name, text).virtualFile

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)

        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val scratchFileEditor = getScratchEditorForSelectedFile(manager!!, myFixture.file.virtualFile)
                                ?: error("Couldn't find scratch panel")

        // We want to check that correct module is selected automatically,
        // that's why we set `module` to null so it wouldn't be changed
        configureOptions(scratchFileEditor, text, null)

        return scratchFileEditor.scratchFile
    }


    protected fun launchScratch() {
        val action = RunScratchAction()
        launchAction(action)
    }

    protected fun launchAction(action: AnAction) {
        val e = getActionEvent(action)
        ActionUtil.updateAction(action, e)
        Assert.assertTrue(e.presentation.isEnabledAndVisible)
        ActionUtil.performAction(action, e)
    }

    protected fun waitUntilScratchFinishes(shouldStopRepl: Boolean = true) {
        UIUtil.dispatchAllInvocationEvents()

        val start = System.currentTimeMillis()
        // wait until output is displayed in editor or for 1 minute
        while (ScratchCompilationSupport.isAnyInProgress()) {
            if ((System.currentTimeMillis() - start) > TIME_OUT) {
                LOG.warn("Waiting timeout $TIME_OUT ms is exceed")
                break
            }
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(100)
        }

        if (shouldStopRepl) stopReplProcess()

        UIUtil.dispatchAllInvocationEvents()
    }

    protected fun stopReplProcess() {
        if (myFixture.file != null) {
            val scratchFile = getScratchEditorForSelectedFile(manager!!, myFixture.file.virtualFile)?.scratchFile
                    as? org.jetbrains.kotlin.idea.jvm.k1.scratch.K1KotlinScratchFile ?: error("Couldn't find scratch panel")
            scratchFile.replScratchExecutor?.stopAndWait()
        }

        UIUtil.dispatchAllInvocationEvents()
    }

    private fun getActionEvent(action: AnAction): AnActionEvent {
        val context = TestDataProvider(project)
        return TestActionEvent.createTestEvent(action, context::getData)
    }

    protected fun doTestScratchText(): String {
        return File(testDataPath, "scripting-support/testData/scratch/custom/test_scratch.kts").readText()
    }

    override fun getProjectDescriptor(): com.intellij.testFramework.LightProjectDescriptor {
        val testName = getTestName(false)

        return when {
            testName.endsWith("WithKotlinTest") -> INSTANCE_WITH_KOTLIN_TEST
            testName.endsWith("NoRuntime") -> INSTANCE_WITHOUT_RUNTIME
            testName.endsWith("ScriptRuntime") -> INSTANCE_WITH_SCRIPT_RUNTIME
            else -> KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
        }
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }

        vfsDisposable = allowProjectRootAccess(this)

        PluginTestCaseBase.addJdk(myFixture.projectDisposable) { PluginTestCaseBase.fullJdk() }
    }

    override fun tearDown() {
        runAll(
            { disposeVfsRootAccess(vfsDisposable) },
            { super.tearDown() },
            {
                runWriteAction {
                    scratchFiles.forEach { it.delete(this) }
                }
            },
        )
    }

    companion object {
        private val TIME_OUT = TimeUnit.MINUTES.toMillis(1)

        private val INSTANCE_WITH_KOTLIN_TEST = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
            arrayListOf(
              TestKotlinArtifacts.kotlinStdlib,
              TestKotlinArtifacts.kotlinTest
            ),
            arrayListOf(TestKotlinArtifacts.kotlinStdlibSources)
        ) {
            override fun getSdk() = PluginTestCaseBase.fullJdk()
        }

        private val INSTANCE_WITHOUT_RUNTIME = object : KotlinLightProjectDescriptor() {
            override fun getSdk() = PluginTestCaseBase.fullJdk()
        }

        private val INSTANCE_WITH_SCRIPT_RUNTIME = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
            arrayListOf(
              TestKotlinArtifacts.kotlinStdlib,
              TestKotlinArtifacts.kotlinScriptRuntime
            ),
            arrayListOf(TestKotlinArtifacts.kotlinStdlibSources)
        ) {
            override fun getSdk() = PluginTestCaseBase.fullJdk()
        }

        fun configureOptions(
            scratchFileEditor: KtScratchFileEditorWithPreview,
            fileText: String,
            module: Module?
        ) {
            val scratchFile = scratchFileEditor.scratchFile

            if (InTextDirectivesUtils.getPrefixedBoolean(fileText, "// INTERACTIVE_MODE: ") != true) {
                scratchFile.saveOptions { copy(isInteractiveMode = false) }
            }

            if (InTextDirectivesUtils.getPrefixedBoolean(fileText, "// REPL_MODE: ") == true) {
                scratchFile.saveOptions { copy(isRepl = true) }
            }

            if (module != null && !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NO_MODULE")) {
                scratchFile.setModule(module)
            }

            val isPreviewEnabled = InTextDirectivesUtils.getPrefixedBoolean(fileText, "// PREVIEW_ENABLED: ") == true
            scratchFileEditor.setPreviewEnabled(isPreviewEnabled)
        }

    }
}
