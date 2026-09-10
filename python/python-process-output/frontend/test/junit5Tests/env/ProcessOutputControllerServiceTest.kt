package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.impl.LoggingProcess
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessOutputControllerService
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.childrenOf
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.io.awaitExit
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.TraceContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
internal class ProcessOutputControllerServiceTest {
  private val projectFixture = projectFixture()

  @Test
  fun `processes are emitted through the frontend topic as expected`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val service = projectFixture.get().service<ProcessOutputControllerService>()
    val binOnEel = BinOnEel(python, cwd)
    val fileContent = { id: Int ->
      """
        import sys
        
        print("process$id out")
        print("process$id err", file=sys.stderr)
      """.trimIndent()
    }
    val treeRoot = service.controller.treeSectionState.treeRoot

    val process1 = binOnEel.runTestScriptFile(cwd, fileContent = fileContent(1))
    val process2 = binOnEel.runTestScriptFile(cwd, fileContent = fileContent(2))
    val process3 = binOnEel.runTestScriptFile(cwd, fileContent = fileContent(3))
    lateinit var loggedProcess1: LoggedProcess
    lateinit var loggedProcess2: LoggedProcess
    lateinit var loggedProcess3: LoggedProcess

    waitUntil {
      loggedProcess1 = treeRoot.value.findProcess(process1.id) ?: return@waitUntil false
      loggedProcess2 = treeRoot.value.findProcess(process2.id) ?: return@waitUntil false
      loggedProcess3 = treeRoot.value.findProcess(process3.id) ?: return@waitUntil false

      true
    }

    for ((index, value) in listOf(
      process1 to loggedProcess1,
      process2 to loggedProcess2,
      process3 to loggedProcess3
    ).withIndex()) {
      val id = index + 1
      val (process, loggedProcess) = value
      val lines = loggedProcess.lines

      assertEquals(process.id, loggedProcess.data.id)
      assertEquals(2, lines.value.size)
      assert(
        lines.value.find { it.text == "process$id out" && it.kind == OutputKindDto.OUT } != null &&
        lines.value.find { it.text == "process$id err" && it.kind == OutputKindDto.ERR } != null
      ) {
        """
          expected to find two lines: 
            OutputLineDto(text="process$id out", kind=OUT)
            OutputLineDto(text="process$id err", kind=ERR)
          actual lines:
            ${lines.value}
        """.trimIndent()
      }
    }
  }

  @Test
  fun `copying to clipboard happens through clipboard manager`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val service = projectFixture.get().service<ProcessOutputControllerService>()
    val binOnEel = BinOnEel(python, cwd)

    val process =
      binOnEel.runTestScriptFile(
        cwd,
        fileContent =
          """
            print("out1")
            print("out2")
            print("out3")
            print("out4")
            print("out5")
            print("out6")
          """.trimIndent()
      )
    lateinit var loggedProcess: LoggedProcess

    waitUntil {
      loggedProcess =
        service.controller.treeSectionState.treeRoot.value.findProcess(process.id)
        ?: return@waitUntil false

      true
    }

    // copying stdout
    service.controller.copyOutputTagAtIndexToClipboard(loggedProcess, 0)

    assertEquals(
      """
        out1
        out2
        out3
        out4
        out5
        out6

      """.trimIndent(),
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
    )
  }

  companion object {
    const val MAIN_PY = "main.py"

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun runBin(binOnEel: BinOnEel, args: Args, context: TraceContext? = NON_INTERACTIVE_ROOT_TRACE_CONTEXT): LoggedProcessDto =
      withContext(context ?: currentCoroutineContext()) {
        val process = ExecService().executeGetProcess(
          binOnEel,
          args,
          GlobalScope,
        ).orThrow() as LoggingProcess

        coroutineScope {
          listOf(
            async(Dispatchers.IO) {
              process.errorStream.readAllBytes()
            },
            async(Dispatchers.IO) {
              process.inputStream.readAllBytes()
            },
          ).awaitAll()

          process.awaitExit()
        }

        process.loggedProcess
      }

    suspend fun BinOnEel.runTestScriptFile(
      cwd: Path,
      filename: String = MAIN_PY,
      fileContent: String = "print('hello, world')",
      context: TraceContext? = null,
    ): LoggedProcessDto {
      val filePath = cwd.resolve(filename)

      edtWriteAction {
        Files.deleteIfExists(filePath)

        val file = Files.createFile(filePath)

        file.toFile().writeText(fileContent)
      }

      return runBin(this, Args(filename), context)
    }

    fun List<ProcessTreeNode>.findProcess(processId: ProcessId): LoggedProcess? {
      for (node in this) {
        when (node) {
          is ProcessTreeNode.Context -> {
            node.childrenOf<ProcessTreeNode>().findProcess(processId)?.also {
              return it
            }
          }
          is ProcessTreeNode.Process -> {
            node.loggedProcess.takeIf { node.nodeId.processId == processId }?.also {
              return it
            }
          }
        }
      }

      return null
    }
  }
}
