package com.intellij.python.processOutput.frontend

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.ui.components.FilterActionGroupState
import com.intellij.python.processOutput.frontend.ui.toggle
import io.mockk.spyk
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.ChildrenGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.TreeBuilder
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.theme.LocalThemeInstanceUuid
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule

internal abstract class ProcessOutputTest {
    private val traceContextCache = mutableMapOf<TraceContextUuid, TraceContextDto>()

    protected val processTree = MutableStateFlow(buildTree<TreeNode> {})

    protected val processOutputInfoExpanded = MutableStateFlow(false)
    protected val processOutputOutputExpanded = MutableStateFlow(true)

    protected val testSelectedProcess: MutableStateFlow<LoggedProcess?> = MutableStateFlow(null)
    protected val testProcessTreeUiState: TreeUiState = run {
        val selectableLazyListState = SelectableLazyListState(LazyListState())
        TreeUiState(
            filters = FilterActionGroupState(TreeFilter),
            searchState = TextFieldState(),
            selectableLazyListState = selectableLazyListState,
            treeState = TreeState(selectableLazyListState),
            tree = processTree,
        )
    }
    protected val testProcessOutputUiState: OutputUiState = OutputUiState(
        filters = FilterActionGroupState(OutputFilter),
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

        override fun resolveTraceContext(uuid: TraceContextUuid): TraceContextDto? =
            traceContextCache[uuid]

        override fun collapseAllContexts() {
            controllerSpy.collapseAllContexts()
        }

        override fun expandAllContexts() {
            controllerSpy.expandAllContexts()
        }

        override fun selectProcess(process: LoggedProcess?) {
            controllerSpy.selectProcess(process)
        }

        override fun onTreeFilterItemToggled(filterItem: TreeFilter.Item, enabled: Boolean) {
            controllerSpy.onTreeFilterItemToggled(filterItem, enabled)
        }

        override fun onOutputFilterItemToggled(filterItem: OutputFilter.Item, enabled: Boolean) {
            controllerSpy.onOutputFilterItemToggled(filterItem, enabled)
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

    fun processOutputTest(body: suspend ComposeContentTestRule.() -> Unit) {
        traceContextCache.clear()
        runTest {
            rule.body()
        }
    }

    fun setTree(builder: suspend TreeBuilder<TreeNode>.() -> Unit) {
        processTree.value = buildTree {
            runBlocking {
                builder()
            }
        }
    }

    fun expandContext(vararg traceContexts: TraceContextDto) {
        testProcessTreeUiState.treeState.openNodes(traceContexts.toList())
    }

    fun toggleTreeFilter(filterItem: TreeFilter.Item) {
        testProcessTreeUiState.filters.active.toggle(filterItem)
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

    private val nextId = AtomicInteger(0)

    fun process(
        vararg command: String,
        traceContext: TraceContextDto? = null,
        startedAt: Instant = Clock.System.now(),
        cwd: String? = null,
        lines: List<OutputLineDto> = listOf(),
        status: ProcessStatus = ProcessStatus.Running,
        weight: ProcessWeightDto? = null,
    ): LoggedProcess =
        LoggedProcess(
            data = LoggedProcessDto(
                weight = weight,
                traceContextUuid =
                    traceContext?.uuid ?: traceContext("some title").uuid,
                pid = 123,
                startedAt = startedAt,
                cwd = cwd,
                exe =
                    ExecutableDto(
                        path = command.first(),
                        parts = command.first().split(Regex("[/\\\\]+")),
                    ),
                args = command.drop(1),
                env = mapOf(),
                target = "Local",
                id = nextId.getAndAdd(1),
            ),
            lines = lines,
            status = MutableStateFlow(status),
        )

    fun outLine(text: String, lineNo: Int): OutputLineDto =
        OutputLineDto(
            text = text,
            kind = OutputKindDto.OUT,
            lineNo = lineNo,
        )

    fun errLine(text: String, lineNo: Int): OutputLineDto =
        OutputLineDto(
            text = text,
            kind = OutputKindDto.ERR,
            lineNo = lineNo,
        )

    fun traceContext(
        title: String,
        kind: TraceContextKind = TraceContextKind.INTERACTIVE,
        parent: TraceContextDto? = null,
    ): TraceContextDto {
        val uuid = TraceContextUuid(UUID.randomUUID().toString())
        val traceContext =
            TraceContextDto(
                title = title,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                uuid = uuid,
                kind = kind,
                parentUuid = parent?.uuid,
            )

        traceContextCache[uuid] = traceContext

        return traceContext
    }

    suspend fun TreeGeneratorScope<TreeNode>.addProcess(
        vararg command: String,
        traceContext: TraceContextDto? = null,
        startedAt: Instant = Clock.System.now(),
        cwd: String? = null,
        weight: ProcessWeightDto? = null,
    ) {
        addProcess(
            process(
                *command,
                traceContext = traceContext,
                startedAt = startedAt,
                cwd = cwd,
                weight = weight,
            ),
        )
    }

    fun TreeGeneratorScope<TreeNode>.addProcess(process: LoggedProcess) {
        addLeaf(TreeNode.Process(process, null), process)
    }

    fun TreeGeneratorScope<TreeNode>.addContext(
        context: TraceContextDto,
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
    exitCode: Int,
    exitedAt: Instant = Clock.System.now(),
): LoggedProcess {
    (status as MutableStateFlow<ProcessStatus>).value = ProcessStatus.Done(
        exitedAt = exitedAt,
        exitCode = exitCode,
    )

    return this
}
