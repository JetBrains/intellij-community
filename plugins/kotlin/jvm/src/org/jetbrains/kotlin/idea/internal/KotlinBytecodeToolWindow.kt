// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.internal

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.Alarm
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.core.ClassFileOrigins
import org.jetbrains.kotlin.idea.core.KotlinCompilerIde
import org.jetbrains.kotlin.idea.util.InfinitePeriodicalTask
import org.jetbrains.kotlin.idea.util.LongRunningReadTask
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.join
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import javax.swing.*
import kotlin.math.min

sealed class BytecodeGenerationResult {
    data class Bytecode(val text: String) : BytecodeGenerationResult()
    data class Error(val text: String) : BytecodeGenerationResult()
}

class KotlinBytecodeToolWindow(private val myProject: Project, private val toolWindow: ToolWindow) : JPanel(BorderLayout()), Disposable {
    @Suppress("JoinDeclarationAndAssignment")
    private val myEditor: Editor
    private val enableInline: JCheckBox
    private val enableOptimization: JCheckBox
    private val enableAssertions: JCheckBox
    private val decompile: JButton
    private val jvmTargets: JComboBox<String>
    private val ir: JCheckBox

    private inner class UpdateBytecodeToolWindowTask : LongRunningReadTask<Location, BytecodeGenerationResult>(this) {
        override fun prepareRequestInfo(): Location? {
            if (!toolWindow.isVisible) {
                return null
            }

            val location = Location.fromEditor(FileEditorManager.getInstance(myProject).selectedTextEditor, myProject)
            if (location.getEditor() == null) {
                return null
            }

            val file = location.kFile
            return if (file == null || !RootKindFilter.projectSources.matches(file)) {
                null
            } else location

        }

        override fun cloneRequestInfo(location: Location): Location {
            val newLocation = super.cloneRequestInfo(location)
            assert(location == newLocation) { "cloneRequestInfo should generate same location object" }
            return newLocation
        }

        override fun hideResultOnInvalidLocation() {
            setText(DEFAULT_TEXT)
        }

        override fun processRequest(location: Location): BytecodeGenerationResult {
            val ktFile = location.kFile!!

            val configuration = CompilerConfiguration()
            if (!enableInline.isSelected) {
                configuration.put(CommonConfigurationKeys.DISABLE_INLINE, true)
            }
            if (!enableAssertions.isSelected) {
                configuration.put(JVMConfigurationKeys.DISABLE_CALL_ASSERTIONS, true)
                configuration.put(JVMConfigurationKeys.DISABLE_PARAM_ASSERTIONS, true)
            }
            if (!enableOptimization.isSelected) {
                configuration.put(JVMConfigurationKeys.DISABLE_OPTIMIZATION, true)
            }

            configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.fromString(jvmTargets.selectedItem as String)!!)

            if (ir.isSelected) {
                configuration.put(JVMConfigurationKeys.IR, true)
            }

            configuration.languageVersionSettings = ktFile.languageVersionSettings

            return getBytecodeForFile(ktFile, configuration)
        }

        override fun onResultReady(requestInfo: Location, result: BytecodeGenerationResult?) {
            val editor = requestInfo.getEditor()!!

            if (result == null) {
                return
            }

            when (result) {
                is BytecodeGenerationResult.Error -> {
                    decompile.isEnabled = false
                    setText(result.text)
                }
                is BytecodeGenerationResult.Bytecode -> {
                    decompile.isEnabled = true
                    setText(result.text)

                    val fileStartOffset = requestInfo.getStartOffset()
                    val fileEndOffset = requestInfo.getEndOffset()

                    val document = editor.document
                    val startLine = document.getLineNumber(fileStartOffset)
                    var endLine = document.getLineNumber(fileEndOffset)
                    if (endLine > startLine && fileEndOffset > 0 && document.charsSequence[fileEndOffset - 1] == '\n') {
                        endLine--
                    }

                    val byteCodeDocument = myEditor.document

                    val linesRange = mapLines(byteCodeDocument.text, startLine, endLine)
                    val endSelectionLineIndex = min(linesRange.second + 1, byteCodeDocument.lineCount)

                    val startOffset = byteCodeDocument.getLineStartOffset(linesRange.first)
                    val endOffset = min(byteCodeDocument.getLineStartOffset(endSelectionLineIndex), byteCodeDocument.textLength)

                    myEditor.caretModel.moveToOffset(endOffset)
                    myEditor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    myEditor.caretModel.moveToOffset(startOffset)
                    myEditor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

                    myEditor.selectionModel.setSelection(startOffset, endOffset)
                }
            }
        }
    }

    init {
        myEditor = EditorFactory.getInstance().createEditor(
            EditorFactory.getInstance().createDocument(""), myProject, JavaFileType.INSTANCE, true
        )
        add(myEditor.component)

        decompile = JButton(KotlinJvmBundle.message("button.text.decompile"))
        /*TODO: try to extract default parameter from compiler options*/
        enableInline = JCheckBox(KotlinJvmBundle.message("checkbox.text.inline"), true)
        enableOptimization = JCheckBox(KotlinJvmBundle.message("checkbox.text.optimization"), true)
        enableAssertions = JCheckBox(KotlinJvmBundle.message("checkbox.text.assertions"), true)
        jvmTargets = ComboBox(JvmTarget.supportedValues().map { it.description }.toTypedArray())
        @NlsSafe
        val description = JvmTarget.DEFAULT.description
        jvmTargets.selectedItem = description
        ir = JCheckBox(KotlinJvmBundle.message("checkbox.text.ir"), false)

        setText(DEFAULT_TEXT)
        initOptionsPanel()
        registerTasksToUpdateToolWindow()
    }

    private fun initOptionsPanel() {
        val optionPanel = JPanel(FlowLayout())
        add(optionPanel, BorderLayout.NORTH)

        val decompilerFacade = KotlinJvmDecompilerFacade.getInstance()
        if (decompilerFacade != null) {
            optionPanel.add(decompile)
            decompile.addActionListener {
                decompileBytecode(decompilerFacade)
            }
        }

        optionPanel.add(enableInline)
        optionPanel.add(enableOptimization)
        optionPanel.add(enableAssertions)
        optionPanel.add(ir)
        optionPanel.add(JLabel(KotlinJvmBundle.message("bytecode.toolwindow.label.jvm.target")))
        optionPanel.add(jvmTargets)
    }

    private fun decompileBytecode(decompilerFacade: KotlinJvmDecompilerFacade) {
        val location = Location.fromEditor(FileEditorManager.getInstance(myProject).selectedTextEditor, myProject)
        val file = location.kFile
        if (file == null) return
        try {
            decompilerFacade.showDecompiledCode(file)
        } catch (ex: DecompileFailedException) {
            LOG.info(ex)
            Messages.showErrorDialog(
                myProject,
                KotlinJvmBundle.message("failed.to.decompile.0.1", file.name, ex),
                KotlinJvmBundle.message("kotlin.bytecode.decompiler")
            )
        }
    }

    private fun registerTasksToUpdateToolWindow() {
        InfinitePeriodicalTask(
            UPDATE_DELAY.toLong(),
            Alarm.ThreadToUse.SWING_THREAD,
            this,
            Computable<LongRunningReadTask<*, *>> { UpdateBytecodeToolWindowTask() }
        ).start()

        listOf(enableInline, enableOptimization, enableAssertions, ir).forEach { checkBox ->
            checkBox.addActionListener {
                updateToolWindowOnOptionChange()
            }
        }

        jvmTargets.addActionListener {
            updateToolWindowOnOptionChange()
        }
    }

    private fun updateToolWindowOnOptionChange() {
        val task = UpdateBytecodeToolWindowTask()
        if (task.init()) {
            task.run()
        }
    }

    private fun setText(resultText: String) {
        ApplicationManager.getApplication().runWriteAction { myEditor.document.setText(StringUtil.convertLineSeparators(resultText)) }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor)
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinBytecodeToolWindow::class.java)

        private const val UPDATE_DELAY = 1000
        private const val DEFAULT_TEXT = "/*\n" +
                "Generated bytecode for Kotlin source file.\n" +
                "No Kotlin source file is opened.\n" +
                "*/"

        // public for tests
        fun getBytecodeForFile(ktFile: KtFile, configuration: CompilerConfiguration): BytecodeGenerationResult {
            val (state, classFileOrigins) = try {
                compileSingleFile(ktFile, configuration)
                    ?: return BytecodeGenerationResult.Error(KotlinJvmBundle.message("cannot.compile.0.to.bytecode", ktFile.name))
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                return BytecodeGenerationResult.Error(printStackTraceToString(e))
            }

            val answer = StringBuilder()

            val diagnostics = state.collectedExtraJvmDiagnostics.all()
            if (!diagnostics.isEmpty()) {
                answer.append("// Backend Errors: \n")
                answer.append("// ================\n")
                for (diagnostic in diagnostics) {
                    answer.append("// Error at ")
                        .append(diagnostic.psiFile.name)
                        .append(join(diagnostic.textRanges, ","))
                        .append(": ")
                        .append(DefaultErrorMessages.render(diagnostic))
                        .append("\n")
                }
                answer.append("// ================\n\n")
            }

            for (outputFile in getRelevantOutputFiles(state, classFileOrigins, ktFile)) {
                answer.append("// ================")
                answer.append(outputFile.relativePath)
                answer.append(" =================\n")
                answer.append(outputFile.asText()).append("\n\n")
            }

            return BytecodeGenerationResult.Bytecode(answer.toString())
        }

        /**
         * Returns a list of output files from [state] that should be shown to the user.
         *
         * An [OutputFile] is linked to its source [KtFile] via [OutputFile.sourceFiles]. However, all source files are physical, while a
         * [KtFile] might not necessarily be physical. As a fallback for non-physical [KtFile]s, [classFileOrigins] are instead used to map
         * the class file name to the original [KtFile]. [classFileOrigins] cannot be used on their own, because some class files are
         * generated without an originating PSI file (see the explanation in [ClassFileOrigins]).
         *
         * If this approach for some reason filters out all output files, the full list is returned defensively.
         */
        private fun getRelevantOutputFiles(state: GenerationState, classFileOrigins: ClassFileOrigins, ktFile: KtFile): List<OutputFile> {
            val sourceFile = File(ktFile.virtualFile.path)
            val outputFiles = state.factory.asList()
            return outputFiles.filter { outputFile ->
                outputFile.sourceFiles.any { it == sourceFile } || classFileOrigins[outputFile.relativePath]?.contains(ktFile) == true
            }.ifEmpty { outputFiles }
        }

        fun compileSingleFile(ktFile: KtFile, initialConfiguration: CompilerConfiguration): Pair<GenerationState, ClassFileOrigins>? =
            KotlinCompilerIde(
                ktFile,
                initialConfiguration,
                ClassBuilderFactories.TEST,
                shouldStubUnboundIrSymbols = true,
            ).compileTracingOrigin()

        private fun mapLines(text: String, startLine: Int, endLine: Int): Pair<Int, Int> {
            @Suppress("NAME_SHADOWING")
            var startLine = startLine
            var byteCodeLine = 0
            var byteCodeStartLine = -1
            var byteCodeEndLine = -1

            val lines = ArrayList<Int>()
            for (line in text.split("\n").dropLastWhile { it.isEmpty() }.map { line -> line.trim { it <= ' ' } }) {
                if (line.startsWith("LINENUMBER")) {
                    val ktLineNum = Scanner(line.substring("LINENUMBER".length)).nextInt() - 1
                    lines.add(ktLineNum)
                }
            }
            lines.sort()

            for (line in lines) {
                if (line >= startLine) {
                    startLine = line
                    break
                }
            }

            for (line in text.split("\n").dropLastWhile { it.isEmpty() }.map { line -> line.trim { it <= ' ' } }) {
                if (line.startsWith("LINENUMBER")) {
                    val ktLineNum = Scanner(line.substring("LINENUMBER".length)).nextInt() - 1

                    if (byteCodeStartLine < 0 && ktLineNum == startLine) {
                        byteCodeStartLine = byteCodeLine
                    }

                    if (byteCodeStartLine > 0 && ktLineNum > endLine) {
                        byteCodeEndLine = byteCodeLine - 1
                        break
                    }
                }

                if (byteCodeStartLine >= 0 && (line.startsWith("MAXSTACK") || line.startsWith("LOCALVARIABLE") || line.isEmpty())) {
                    byteCodeEndLine = byteCodeLine - 1
                    break
                }


                byteCodeLine++
            }

            return if (byteCodeStartLine == -1 || byteCodeEndLine == -1) {
                Pair(0, 0)
            } else {
                Pair(byteCodeStartLine, byteCodeEndLine)
            }
        }

        private fun printStackTraceToString(e: Throwable): String {
            val out = StringWriter(1024)
            PrintWriter(out).use { printWriter ->
                e.printStackTrace(printWriter)
                return out.toString().replace("\r", "")
            }
        }
    }
}
