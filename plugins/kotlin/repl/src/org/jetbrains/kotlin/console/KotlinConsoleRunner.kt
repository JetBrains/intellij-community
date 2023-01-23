// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.console

import com.intellij.execution.Executor
import com.intellij.execution.console.ConsoleExecuteAction
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.console.actions.BuildAndRestartConsoleAction
import org.jetbrains.kotlin.console.actions.KtExecuteCommandAction
import org.jetbrains.kotlin.console.gutter.ConsoleGutterContentProvider
import org.jetbrains.kotlin.console.gutter.ConsoleIndicatorRenderer
import org.jetbrains.kotlin.console.gutter.IconWithTooltip
import org.jetbrains.kotlin.console.gutter.ReplIcons
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.forcedModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.trackers.KOTLIN_CONSOLE_KEY
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionContributor
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionSourceAsContributor
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.repl.ReplState
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.awt.Color
import java.awt.Font
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

private const val KOTLIN_SHELL_EXECUTE_ACTION_ID = "KotlinShellExecute"

class KotlinConsoleRunner(
    val module: Module,
    private val environmentRequest: TargetEnvironmentRequest,
    private val cmdLine: TargetedCommandLine,
    internal val previousCompilationFailed: Boolean,
    myProject: Project,
    @Nls(capitalization = Nls.Capitalization.Title) title: String,
    path: String?
) : AbstractConsoleRunnerWithHistory<LanguageConsoleView>(myProject, title, path) {

    private val replState = ReplState()
    private val consoleTerminated = CountDownLatch(1)
    private lateinit var environment: TargetEnvironment

    override fun finishConsole() {
        KotlinConsoleKeeper.getInstance(project).removeConsole(consoleView.virtualFile)
        val consoleContributor = ScriptDefinitionContributor.find<ConsoleScriptDefinitionContributor>(project)!!
        consoleContributor.unregisterDefinition(consoleScriptDefinition)
        ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(consoleContributor)

        if (isUnitTestMode()) {
            consoleTerminated.countDown()
            // Ignore super with myConsoleView.setEditable(false)
            return
        }

        super.finishConsole()
    }

    val commandHistory = CommandHistory()

    var isReadLineMode: Boolean = false
        set(value) {
            if (value)
                changeConsoleEditorIndicator(ReplIcons.EDITOR_READLINE_INDICATOR)
            else
                changeConsoleEditorIndicator(ReplIcons.EDITOR_INDICATOR)

            field = value
        }

    private fun changeConsoleEditorIndicator(newIconWithTooltip: IconWithTooltip) = WriteCommandAction.runWriteCommandAction(project) {
        consoleEditorHighlighter.gutterIconRenderer = ConsoleIndicatorRenderer(newIconWithTooltip)
    }

    private var consoleEditorHighlighter by Delegates.notNull<RangeHighlighter>()
    private var disposableDescriptor by Delegates.notNull<RunContentDescriptor>()

    val executor = CommandExecutor(this)
    var compilerHelper: ConsoleCompilerHelper by Delegates.notNull()

    private val consoleScriptDefinition = object : KotlinScriptDefinition(Any::class) {
        override val name get() = KotlinIdeaReplBundle.message("name.kotlin.repl")
        override fun isScript(fileName: String): Boolean = fileName.startsWith(consoleView.virtualFile.name)
        override fun getScriptName(script: KtScript) = Name.identifier("REPL")
    }

    override fun createProcess(): Process {
        environment = environmentRequest.prepareEnvironment(TargetProgressIndicator.EMPTY)
        return environment.createProcess(cmdLine, ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator())
    }

    override fun createConsoleView(): LanguageConsoleView {
        val builder = LanguageConsoleBuilder()

        val consoleView = builder.gutterContentProvider(ConsoleGutterContentProvider()).build(project, KotlinLanguage.INSTANCE)

        // This rename is needed to parse file in console as script
        consoleView.virtualFile.rename(this, consoleView.virtualFile.name + KotlinParserDefinition.STD_SCRIPT_EXT)

        consoleView.virtualFile.putUserData(KOTLIN_CONSOLE_KEY, true)


        consoleView.prompt = null

        val consoleEditor = consoleView.consoleEditor

        setupPlaceholder(consoleEditor)
        val historyKeyListener = HistoryKeyListener(module.project, consoleEditor, commandHistory)
        consoleEditor.contentComponent.addKeyListener(historyKeyListener)
        commandHistory.listeners.add(historyKeyListener)

        val executeAction = KtExecuteCommandAction(consoleView.virtualFile)
        executeAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, consoleView.consoleEditor.component)

        val consoleContributor = ScriptDefinitionContributor.find<ConsoleScriptDefinitionContributor>(project)!!
        consoleContributor.registerDefinition(consoleScriptDefinition)
        ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(consoleContributor)

        enableCompletion(consoleView)

        setupGutters(consoleView)

        return consoleView
    }

    private fun enableCompletion(consoleView: LanguageConsoleView) {
        val consoleKtFile = PsiManager.getInstance(project).findFile(consoleView.virtualFile) as? KtFile ?: return
        configureFileDependencies(consoleKtFile)
    }

    override fun createProcessHandler(process: Process): OSProcessHandler {
        val processHandler = ReplOutputHandler(
            this,
            process,
            cmdLine.getCommandPresentation(environment)
        )
        val consoleFile = consoleView.virtualFile
        val keeper = KotlinConsoleKeeper.getInstance(project)

        keeper.putVirtualFileToConsole(consoleFile, this)

        return processHandler
    }

    override fun createExecuteActionHandler() = object : ProcessBackedConsoleExecuteActionHandler(processHandler, false) {
        override fun runExecuteAction(consoleView: LanguageConsoleView) = executor.executeCommand()
    }

    override fun fillToolBarActions(
        toolbarActions: DefaultActionGroup,
        defaultExecutor: Executor,
        contentDescriptor: RunContentDescriptor
    ): List<AnAction> {
        disposableDescriptor = contentDescriptor
        compilerHelper = ConsoleCompilerHelper(project, module, defaultExecutor, contentDescriptor)

        val actionList = arrayListOf<AnAction>(
            BuildAndRestartConsoleAction(this),
            createConsoleExecAction(consoleExecuteActionHandler),
            createCloseAction(defaultExecutor, contentDescriptor)
        )
        toolbarActions.addAll(actionList)
        return actionList
    }

    override fun createConsoleExecAction(consoleExecuteActionHandler: ProcessBackedConsoleExecuteActionHandler) =
        ConsoleExecuteAction(consoleView, consoleExecuteActionHandler, KOTLIN_SHELL_EXECUTE_ACTION_ID, consoleExecuteActionHandler)

    override fun constructConsoleTitle(title: String) = KotlinIdeaReplBundle.message("constructor.title.0.in.module.1", title, module.name)

    private fun setupPlaceholder(editor: EditorEx) {
        val executeCommandAction = ActionManager.getInstance().getAction(KOTLIN_SHELL_EXECUTE_ACTION_ID)
        val executeCommandActionShortcutText = KeymapUtil.getFirstKeyboardShortcutText(executeCommandAction)

        editor.setPlaceholder(KotlinIdeaReplBundle.message("command.0.to.execute", executeCommandActionShortcutText))
        editor.setShowPlaceholderWhenFocused(true)

        val placeholderAttrs = TextAttributes()
        placeholderAttrs.foregroundColor = ReplColors.PLACEHOLDER_COLOR
        placeholderAttrs.fontType = Font.ITALIC
        editor.setPlaceholderAttributes(placeholderAttrs)
    }

    private fun setupGutters(consoleView: LanguageConsoleView) {
        fun configureEditorGutter(editor: EditorEx, color: Color, iconWithTooltip: IconWithTooltip): RangeHighlighter {
            editor.settings.isLineMarkerAreaShown = true // hack to show gutter
            editor.settings.isFoldingOutlineShown = true
            editor.gutterComponentEx.setPaintBackground(true)
            val editorColorScheme = editor.colorsScheme
            editorColorScheme.setColor(EditorColors.GUTTER_BACKGROUND, color)
            editor.colorsScheme = editorColorScheme

            return addGutterIndicator(editor, iconWithTooltip)
        }

        val historyEditor = consoleView.historyViewer
        val consoleEditor = consoleView.consoleEditor

        configureEditorGutter(historyEditor, ReplColors.HISTORY_GUTTER_COLOR, ReplIcons.HISTORY_INDICATOR)
        consoleEditorHighlighter = configureEditorGutter(consoleEditor, ReplColors.EDITOR_GUTTER_COLOR, ReplIcons.EDITOR_INDICATOR)

        historyEditor.settings.isUseSoftWraps = true
        historyEditor.settings.additionalLinesCount = 0

        consoleEditor.settings.isCaretRowShown = true
        consoleEditor.settings.additionalLinesCount = 2
    }

    fun addGutterIndicator(editor: EditorEx, iconWithTooltip: IconWithTooltip): RangeHighlighter {
        val indicator = ConsoleIndicatorRenderer(iconWithTooltip)
        val editorMarkup = editor.markupModel
        val indicatorHighlighter = editorMarkup.addRangeHighlighter(
            0, editor.document.textLength, HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE
        )

        return indicatorHighlighter.apply { gutterIconRenderer = indicator }
    }

    @TestOnly
    fun dispose() {
        processHandler.destroyProcess()
        consoleTerminated.await(1, TimeUnit.SECONDS)
        Disposer.dispose(disposableDescriptor)
    }

    fun successfulLine(text: String) {
        project.runReadActionInSmartMode {
            val lineNumber = replState.successfulLinesCount + 1
            val virtualFile =
                LightVirtualFile(
                    "${consoleView.virtualFile.name}$lineNumber${KotlinParserDefinition.STD_SCRIPT_EXT}",
                    KotlinLanguage.INSTANCE, text
                ).apply {
                    charset = CharsetToolkit.UTF8_CHARSET
                    isWritable = false
                }
            val psiFile = (PsiFileFactory.getInstance(project) as PsiFileFactoryImpl).trySetupPsiForFile(
                virtualFile,
                KotlinLanguage.INSTANCE,
                true,
                false
            ) as KtFile? ?: error("Failed to setup PSI for file:\n$text")

            replState.submitLine(psiFile)
            configureFileDependencies(psiFile)
            val scriptDescriptor =
                psiFile.script!!.unsafeResolveToDescriptor() as? ScriptDescriptor ?: error("Failed to analyze line:\n$text")
            ForceResolveUtil.forceResolveAllContents(scriptDescriptor)
            replState.lineSuccess(psiFile, scriptDescriptor)

            replState.submitLine(consoleFile) // reset file scope customizer
        }
    }

    val consoleFile: KtFile
        get() {
            val consoleFile = consoleView.virtualFile
            return PsiManager.getInstance(project).findFile(consoleFile) as KtFile
        }

    private fun configureFileDependencies(psiFile: KtFile) {
        psiFile.forcedModuleInfo = module.testSourceInfo
            ?: module.productionSourceInfo
            ?: NotUnderContentRootModuleInfo(psiFile.project, psiFile)
    }
}

class ConsoleScriptDefinitionContributor : ScriptDefinitionSourceAsContributor {

    private val definitionsSet = ContainerUtil.newConcurrentSet<ScriptDefinition>()

    override val definitions: Sequence<ScriptDefinition>
        get() = definitionsSet.asSequence()

    override val id: String = "IDEA Console"

    // TODO: rewrite to ScriptDefinition
    fun registerDefinition(definition: KotlinScriptDefinition) {
        definitionsSet.add(ScriptDefinition.FromLegacy(defaultJvmScriptingHostConfiguration, definition))
    }

    fun unregisterDefinition(definition: KotlinScriptDefinition) {
        definitionsSet.removeIf { it.asLegacyOrNull<KotlinScriptDefinition>() == definition }
    }
}