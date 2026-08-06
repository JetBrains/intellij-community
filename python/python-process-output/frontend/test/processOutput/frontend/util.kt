package com.intellij.python.processOutput.frontend

import androidx.compose.foundation.ScrollState
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
import io.mockk.spyk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.foundation.theme.LocalThemeInstanceUuid
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule

internal abstract class ProcessOutputTest {
  private val traceContextCache = mutableMapOf<TraceContextUuid, TraceContextDto>()

  protected val processOutputInfoExpanded = MutableStateFlow(false)
  protected val processOutputOutputExpanded = MutableStateFlow(true)

  protected val testSearch: MutableStateFlow<String> = MutableStateFlow("")
  protected val testSelectedProcess: MutableStateFlow<LoggedProcess?> = MutableStateFlow(null)
  protected val testProcessTreeUiState: TreeUiState =
    TreeUiState(
      filters = FilterActionGroupState(TreeFilter),
      searchQuery = testSearch,
      treeRoot = MutableStateFlow(emptyList()),
    )
  protected val testProcessOutputUiState: OutputUiState = OutputUiState(
    filters = FilterActionGroupState(OutputFilter),
    isInfoExpanded = processOutputInfoExpanded,
    isOutputExpanded = processOutputOutputExpanded,
    verticalScrollState = ScrollState(0),
    horizontalScrollState = ScrollState(0),
  )

  @get:Rule
  val rule: ComposeContentTestRule = createComposeRule()

  val controllerSpy = spyk<ProcessOutputController>()

  val controller = object : ProcessOutputController {
    override val selectedProcess: StateFlow<LoggedProcess?> = testSelectedProcess
    override val processStatusUpdates: Flow<LoggedProcess> = MutableSharedFlow()
    override val processTreeUiState: TreeUiState = testProcessTreeUiState
    override val processOutputUiState: OutputUiState = testProcessOutputUiState


    override fun search(query: String) {
      controllerSpy.search(query)
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

  fun setSelectedProcess(process: LoggedProcess) {
    testSelectedProcess.value = process
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
    env: Map<String, String> = mapOf(),
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
        env = env,
        target = "Local",
        id = nextId.getAndAdd(1),
      ),
      lines = lines,
      status = MutableStateFlow(status),
    )

  fun outLine(text: String): OutputLineDto =
    OutputLineDto(
      text = text,
      kind = OutputKindDto.OUT,
    )

  fun errLine(text: String): OutputLineDto =
    OutputLineDto(
      text = text,
      kind = OutputKindDto.ERR,
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
