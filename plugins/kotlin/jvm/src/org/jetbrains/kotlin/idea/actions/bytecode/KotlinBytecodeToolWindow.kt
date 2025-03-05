// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions.bytecode

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.descriptors.components.STUB_UNBOUND_IR_SYMBOLS
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.base.codeInsight.compiler.KotlinCompilerIdeAllowedErrorFilter
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.internal.DecompileFailedException
import org.jetbrains.kotlin.idea.internal.KotlinJvmDecompilerFacade
import org.jetbrains.kotlin.idea.util.LongRunningReadTask
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import javax.swing.*
import kotlin.math.min

@ApiStatus.Internal
sealed class BytecodeGenerationResult {
    data class Bytecode(val text: String) : BytecodeGenerationResult()
    data class Error(val text: String) : BytecodeGenerationResult()
}

@ApiStatus.Internal
class KotlinBytecodeToolWindow(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()), Disposable {
    private val document = EditorFactory.getInstance().createDocument("")
    private val editor = EditorFactory.getInstance().createEditor(document, project, JavaFileType.INSTANCE, /* isViewer = */ true)

    private val enableInline: JCheckBox
    private val enableOptimization: JCheckBox
    private val enableAssertions: JCheckBox
    private val showOffsets: JCheckBox
    private val decompile: JButton
    private val jvmTargets: JComboBox<String>

    private inner class UpdateBytecodeToolWindowTask : LongRunningReadTask<Location, BytecodeGenerationResult>(this) {
        override fun prepareRequestInfo(): Location? {
            if (!toolWindow.isVisible) {
                return null
            }

            val location = Location.fromEditor(FileEditorManager.getInstance(project).selectedTextEditor, project)
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

            val containingModule = ktFile.module
            if (containingModule != null) {
                configuration.put(CommonConfigurationKeys.MODULE_NAME, containingModule.name)
            }

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

            configuration.languageVersionSettings = ktFile.languageVersionSettings

            return getBytecodeForFile(ktFile, configuration, showOffsets.isSelected)
        }

        override fun onResultReady(requestInfo: Location, result: BytecodeGenerationResult?) {
            val sourceEditor = requestInfo.getEditor()!!

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

                    val sourceDocument = sourceEditor.document
                    val startLine = sourceDocument.getLineNumber(fileStartOffset)
                    var endLine = sourceDocument.getLineNumber(fileEndOffset)
                    if (endLine > startLine && fileEndOffset > 0 && sourceDocument.charsSequence[fileEndOffset - 1] == '\n') {
                        endLine--
                    }

                    val linesRange = mapLines(document.text, startLine, endLine)
                    val endSelectionLineIndex = min(linesRange.second + 1, document.lineCount)

                    val startOffset = document.getLineStartOffset(linesRange.first)
                    val endOffset = min(document.getLineStartOffset(endSelectionLineIndex), document.textLength)

                    editor.caretModel.moveToOffset(endOffset)
                    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    editor.caretModel.moveToOffset(startOffset)
                    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    editor.selectionModel.setSelection(startOffset, endOffset)
                }
            }
        }
    }

    init {
        editor.setBorder(null)
        add(editor.component)

        decompile = JButton(KotlinJvmBundle.message("button.text.decompile"))
        /*TODO: try to extract default parameter from compiler options*/
        enableInline = JCheckBox(KotlinJvmBundle.message("checkbox.text.inline"), true)
        enableOptimization = JCheckBox(KotlinJvmBundle.message("checkbox.text.optimization"), true)
        showOffsets = JCheckBox(KotlinJvmBundle.message("checkbox.text.offsets"), false)
        enableAssertions = JCheckBox(KotlinJvmBundle.message("checkbox.text.assertions"), true)
        jvmTargets = ComboBox(JvmTarget.supportedValues().map { it.description }.toTypedArray())

        @NlsSafe
        val description = JvmTarget.DEFAULT.description
        jvmTargets.selectedItem = description

        setText(DEFAULT_TEXT)
        initOptionsPanel()
        registerTasksToUpdateToolWindow()
    }

    private fun initOptionsPanel() {
        val optionPanel = JPanel(FlowLayout())
        add(optionPanel, BorderLayout.NORTH)

        with(optionPanel) {
            border = JBUI.Borders.customLineBottom(JBColor.border())

            val decompilerFacade = KotlinJvmDecompilerFacade.getInstance()
            if (decompilerFacade != null) {
                add(decompile)
                decompile.addActionListener {
                    decompileBytecode(decompilerFacade)
                }
            }

            add(enableInline)
            add(enableOptimization)
            add(showOffsets)
            add(enableAssertions)

            add(JLabel(KotlinJvmBundle.message("bytecode.toolwindow.label.jvm.target")))
            add(jvmTargets)
        }
    }

    private fun decompileBytecode(decompilerFacade: KotlinJvmDecompilerFacade) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val location = Location.fromEditor(editor, project)
        val file = location.kFile ?: return

        try {
            decompilerFacade.showDecompiledCode(file)
        } catch (ex: DecompileFailedException) {
            LOG.info(ex)
            Messages.showErrorDialog(
                project,
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

        listOfNotNull(enableInline, enableOptimization, enableAssertions, showOffsets).forEach { checkBox ->
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
        ApplicationManager.getApplication().runWriteAction { editor.document.setText(StringUtil.convertLineSeparators(resultText)) }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinBytecodeToolWindow::class.java)

        private const val UPDATE_DELAY = 1000
        private const val DEFAULT_TEXT = "/*\n" +
                "Generated bytecode for Kotlin source file.\n" +
                "No Kotlin source file is opened.\n" +
                "*/"

        @OptIn(KaExperimentalApi::class)
        fun getBytecodeForFile(
            ktFile: KtFile,
            configuration: CompilerConfiguration,
            showOffsets: Boolean
        ): BytecodeGenerationResult = analyze(ktFile) {
            val (result, classFileOrigins) = try {
                compileSingleFile(ktFile, configuration)
                    ?: return BytecodeGenerationResult.Error(KotlinJvmBundle.message("cannot.compile.0.to.bytecode", ktFile.name))
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                return BytecodeGenerationResult.Error(printStackTraceToString(e))
            }

            val writer = StringWriter()

            when (result) {
                is KaCompilationResult.Success -> {
                    val printWriter = PrintWriter(writer)

                    for (outputFile in getRelevantClassFiles(ktFile, result.output, classFileOrigins)) {
                        writer.append("// ================")
                        writer.append(outputFile.path)
                        writer.append(" =================\n")

                        var offset = 0
                        val classReader = object: ClassReader(outputFile.content) {
                            override fun readBytecodeInstructionOffset(bytecodeOffset: Int) {
                                offset = bytecodeOffset
                            }
                        }
                        val textifier = if(showOffsets) TextifierWithOffsets { offset } else Textifier()
                        val traceVisitor = TraceClassVisitor(null, textifier, printWriter)
                        classReader.accept(traceVisitor, 0)

                        writer.append("\n\n")
                    }
                }
                is KaCompilationResult.Failure -> {
                    val diagnostics = result.errors
                    if (diagnostics.isNotEmpty()) {
                        writer.append("// Backend Errors: \n")
                        writer.append("// ================\n")
                        for (error in diagnostics) {
                            writer.append("// Error")
                            if (error is KaDiagnosticWithPsi<*>) {
                                writer.append(" at ")
                                writer.append(error.psi.containingFile.name)
                                error.textRanges.joinTo(writer)
                            }
                            writer.append(": ")
                            writer.append(error.defaultMessage)
                            writer.append("\n")
                        }
                        writer.append("// ================\n\n")
                    }
                }
            }

            return BytecodeGenerationResult.Bytecode(writer.toString())
        }

        /**
         * Returns a list of class files from [outputFiles] that should be shown to the user.
         *
         * An [KaCompiledFile] is linked to its source [KtFile] via [KaCompiledFile.sourceFiles].
         * However, all source files are physical, while a [KtFile] might not necessarily be physical.
         * As a fallback for non-physical [KtFile]s, [classFileOrigins] are instead used to map the class file name to the original [KtFile].
         * [classFileOrigins] cannot be used on their own, because some class files are generated without an originating PSI file
         * (see the explanation in [ClassFileOrigins]).
         *
         * If this approach for some reason filters out all output files, the full list is returned defensively.
         */
        @KaExperimentalApi
        private fun getRelevantClassFiles(
            ktFile: KtFile,
            outputFiles: List<KaCompiledFile>,
            classFileOrigins: ClassFileOrigins
        ): List<KaCompiledFile> {
            val classFiles = outputFiles.filter { it.isClassFile }
            val sourceFile = File(ktFile.virtualFile.path)

            return classFiles
                .filter { sourceFile in it.sourceFiles || ktFile in classFileOrigins[it.path].orEmpty() }
                .ifEmpty { classFiles }
        }

        @KaExperimentalApi
        @ApiStatus.Internal
        fun KaSession.compileSingleFile(
            ktFile: KtFile,
            configuration: CompilerConfiguration
        ): Pair<KaCompilationResult, ClassFileOrigins>? {
            val effectiveConfiguration = configuration
                .copy()
                .apply {
                    put(STUB_UNBOUND_IR_SYMBOLS, true)
                }

            val classFileOrigins = mutableMapOf<String, MutableSet<PsiFile>>()
            val compilerTarget = KaCompilerTarget.Jvm(isTestMode = true) { file, className ->
                if (file != null) {
                    classFileOrigins.computeIfAbsent("$className.class") { _ -> mutableSetOf() }.add(file)
                }
            }
            val allowedErrorFilter = KotlinCompilerIdeAllowedErrorFilter.getInstance()

            try {
                val result = compile(ktFile, effectiveConfiguration, compilerTarget, allowedErrorFilter)
                return Pair(result, classFileOrigins)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Throwable) {
                LOG.error(e)
                return null
            }
        }

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

private class TextifierWithOffsets(val offsetFn: () -> Int) : Textifier(Opcodes.ASM9) {
    override fun visitInsn(opcode: Int) {
        printOffset()
        super.visitInsn(opcode)
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        printOffset()
        super.visitIntInsn(opcode, operand)
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        printOffset()
        super.visitVarInsn(opcode, varIndex)
    }

    override fun visitTypeInsn(opcode: Int, type: String?) {
        printOffset()
        super.visitTypeInsn(opcode, type)
    }

    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
        printOffset()
        super.visitFieldInsn(opcode, owner, name, descriptor)
    }

    override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
        printOffset()
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    override fun visitJumpInsn(opcode: Int, label: Label?) {
        printOffset()
        super.visitJumpInsn(opcode, label)
    }

    private fun printOffset() {
        text.add("[${offsetFn()}]")
    }

    override fun createTextifier(): Textifier {
        return TextifierWithOffsets(offsetFn)
    }
}

/**
 * Maps the file name of each generated class (e.g. `com/example/MyClass.class`) to the [PsiFile] it originated from.
 *
 * The map will not include entries for class files which are generated with [JvmDeclarationOrigin.NO_ORIGIN] and thus have no containing
 * [PsiFile]. For example, inlined anonymous objects generated by the IR backend have no declaration origin.
 */
private typealias ClassFileOrigins = Map<String, Set<PsiFile>>
