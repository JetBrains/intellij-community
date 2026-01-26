package com.intellij.python.processOutput.impl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.intellij.python.community.execService.impl.LoggedProcess
import com.intellij.python.community.execService.impl.LoggedProcessExe
import com.intellij.python.community.execService.impl.LoggedProcessExitInfo
import com.intellij.python.community.execService.impl.LoggedProcessLine
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.processOutput.impl.ui.toggle
import com.jetbrains.python.TraceContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import io.mockk.spyk
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.foundation.lazy.tree.ChildrenGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.TreeBuilder
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.theme.LocalThemeInstanceUuid
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule

internal abstract class ProcessOutputTest {
    protected val processTree = MutableStateFlow(buildTree<TreeNode> {})
    protected val processTreeFilters: SnapshotStateSet<TreeFilter> = mutableStateSetOf(
        TreeFilter.ShowTime,
    )

    protected val processOutputFilters: SnapshotStateSet<OutputFilter> = mutableStateSetOf(
        OutputFilter.ShowTags,
    )
    protected val processOutputInfoExpanded = MutableStateFlow(false)
    protected val processOutputOutputExpanded = MutableStateFlow(true)

    protected val testSelectedProcess: MutableStateFlow<LoggedProcess?> = MutableStateFlow(null)
    protected val testProcessTreeUiState: TreeUiState = run {
        val selectableLazyListState = SelectableLazyListState(LazyListState())
        TreeUiState(
            filters = processTreeFilters,
            searchState = TextFieldState(),
            selectableLazyListState = selectableLazyListState,
            treeState = TreeState(selectableLazyListState),
            tree = processTree,
        )
    }
    protected val testProcessOutputUiState: OutputUiState = OutputUiState(
        filters = processOutputFilters,
        isInfoExpanded = processOutputInfoExpanded,
        isOutputExpanded = processOutputOutputExpanded,
        lazyListState = LazyListState(),
    )

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    val controllerSpy = spyk<ProcessOutputController>()

    val controller = object : ProcessOutputController {
        override val selectedProcess: StateFlow<LoggedProcess?> = testSelectedProcess
        override val processTreeUiState: TreeUiState = testProcessTreeUiState
        override val processOutputUiState: OutputUiState = testProcessOutputUiState

        override fun collapseAllContexts() {
            controllerSpy.collapseAllContexts()
        }

        override fun expandAllContexts() {
            controllerSpy.expandAllContexts()
        }

        override fun selectProcess(process: LoggedProcess?) {
            controllerSpy.selectProcess(process)
        }

        override fun toggleTreeFilter(filter: TreeFilter) {
            controllerSpy.toggleTreeFilter(filter)
        }

        override fun toggleOutputFilter(filter: OutputFilter) {
            controllerSpy.toggleOutputFilter(filter)
        }

        override fun toggleProcessInfo() {
            controllerSpy.toggleProcessInfo()
        }

        override fun toggleProcessOutput() {
            controllerSpy.toggleProcessOutput()
        }

        override fun copyOutputToClipboard(loggedProcess: LoggedProcess) {
            controllerSpy.copyOutputToClipboard(loggedProcess)
        }

        override fun copyOutputTagAtIndexToClipboard(loggedProcess: LoggedProcess, fromIndex: Int) {
            controllerSpy.copyOutputTagAtIndexToClipboard(loggedProcess, fromIndex)
        }

        override fun copyOutputExitInfoToClipboard(loggedProcess: LoggedProcess) {
            controllerSpy.copyOutputExitInfoToClipboard(loggedProcess)
        }

        override fun specifyAdditionalMessageToUser(logId: Int, message: String) {
            controllerSpy.specifyAdditionalMessageToUser(logId, message)
        }

        override fun tryOpenLogInToolWindow(logId: Int): Boolean {
            return controllerSpy.tryOpenLogInToolWindow(logId)
        }
    }

    fun scaffoldTestContent(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalThemeInstanceUuid provides UUID.randomUUID()) {
                IntUiTheme {
                    content()
                }
            }
        }
    }

    fun processOutputTest(body: suspend ComposeContentTestRule.() -> Unit) =
        runTest {
            rule.body()
        }

    fun setTree(builder: suspend TreeBuilder<TreeNode>.() -> Unit) {
        processTree.value = buildTree {
            runBlocking {
                builder()
            }
        }
    }

    fun expandContext(vararg traceContexts: TraceContext) {
        testProcessTreeUiState.treeState.openNodes(traceContexts.toList())
    }

    fun toggleTreeFilter(filter: TreeFilter) {
        processTreeFilters.toggle(filter)
    }

    fun setSelectedProcess(process: LoggedProcess) {
        testSelectedProcess.value = process
    }

    fun setInfoSectionExpanded(value: Boolean) {
        processOutputInfoExpanded.value = value
    }

    fun setOutputSectionExpanded(value: Boolean) {
        processOutputOutputExpanded.value = value
    }

    suspend fun process(
        vararg command: String,
        traceContext: TraceContext? = null,
        startedAt: Instant = Clock.System.now(),
        cwd: String? = null,
        lines: List<LoggedProcessLine> = listOf(),
        exitInfo: LoggedProcessExitInfo? = null,
    ): LoggedProcess =
        LoggedProcess(
            traceContext = traceContext ?: TraceContext("some title"),
            pid = 123,
            startedAt = startedAt,
            cwd = cwd,
            exe = LoggedProcessExe(
                path = command.first(),
                parts = command.first().split(Regex("[/\\\\]+")),
            ),
            args = command.drop(1),
            env = mapOf(),
            target = "Local",
            lines = run {
                val flow = MutableSharedFlow<LoggedProcessLine>(replay = LoggingLimits.MAX_LINES)

                lines.forEach { flow.emit(it) }

                flow
            },
            exitInfo = MutableStateFlow(exitInfo),
        )

    fun outLine(text: String): LoggedProcessLine =
        LoggedProcessLine(
            text = text,
            kind = LoggedProcessLine.Kind.OUT,
        )

    fun errLine(text: String): LoggedProcessLine =
        LoggedProcessLine(
            text = text,
            kind = LoggedProcessLine.Kind.ERR,
        )

    suspend fun TreeGeneratorScope<TreeNode>.addProcess(
        vararg command: String,
        traceContext: TraceContext? = null,
        startedAt: Instant = Clock.System.now(),
        cwd: String? = null,
    ) {
        addProcess(
            process(
                *command,
                traceContext = traceContext,
                startedAt = startedAt,
                cwd = cwd,
            ),
        )
    }

    fun TreeGeneratorScope<TreeNode>.addProcess(process: LoggedProcess) {
        addLeaf(TreeNode.Process(process), process)
    }

    fun TreeGeneratorScope<TreeNode>.addContext(
        context: TraceContext,
        childrenGenerator: suspend ChildrenGeneratorScope<TreeNode>.() -> Unit,
    ) {
        addNode(TreeNode.Context(context), context) {
            runBlocking {
                childrenGenerator()
            }
        }
    }
}

internal fun LoggedProcess.finish(
    exitValue: Int,
    exitedAt: Instant = Clock.System.now(),
): LoggedProcess {
    exitInfo.value = LoggedProcessExitInfo(
        exitedAt = exitedAt,
        exitValue = exitValue,
    )

    return this
}
