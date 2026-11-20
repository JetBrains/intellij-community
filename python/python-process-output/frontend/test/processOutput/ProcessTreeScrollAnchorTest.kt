package com.intellij.python.processOutput

import com.intellij.ide.ui.LafManager
import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputTopic
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.python.processOutput.frontend.ui.components.ProcessTree
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@TestApplication
internal class ProcessTreeScrollAnchorTest {
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  @TestFor(issues = ["PY-92912"])
  @Test
  fun `a scrolled tree without rows shows the first process`(): Unit = timeoutRunBlocking(2.minutes) {
    val context = ProcessOutputUiContext(projectFixture.get(), JPanel(), disposableFixture.get())
    val tree = withContext(Dispatchers.EDT) {
      // The headless `BasicTreeUI` renders the sample model of `Tree()`, and `ProcessTreeCellRenderer` accepts only `ProcessTreeNode`.
      // `LafManager` installs the tree UI of the IDE, which does not.
      LafManager.getInstance()
      val scrollPane = ProcessTree(context).component as JScrollPane
      val tree = checkNotNull(context.processTree)
      // Without a tall view, a layout moves the scroll position back to 0 before the first update of the tree.
      tree.preferredSize = Dimension(200, 1000)
      scrollPane.setSize(200, 50)
      scrollPane.viewport.viewPosition = Point(0, 100)
      tree
    }

    // The topic does not replay events, so the test sends the process again until the controller collects it.
    waitUntil("the tree shows the process", timeout = 30.seconds) {
      if (withContext(Dispatchers.EDT) { tree.rowCount } > 0) return@waitUntil true
      ProcessOutputTopic.sendNewProcessEvent(processDto(PROCESS_ID), emptyList())
      false
    }
  }
}

private const val PROCESS_ID = 4242

private fun processDto(id: Int): LoggedProcessDto = LoggedProcessDto(
  weight = null,
  traceContextUuid = null,
  pid = null,
  startedAt = Instant.fromEpochMilliseconds(id.toLong()),
  cwd = null,
  exe = ExecutableDto(path = "bin/exe", parts = listOf("bin", "exe")),
  args = emptyList(),
  env = emptyMap(),
  target = "",
  id = ProcessId(id),
)
