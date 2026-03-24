package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.frontend.CoroutineNames
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.ProcessOutputControllerService
import com.intellij.python.processOutput.frontend.ProcessOutputControllerServiceLimits
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.ui.toggle
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.io.awaitExit
import com.intellij.util.system.OS
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir

@PyEnvTestCase
class ProcessOutputControllerServiceTest {
    private val projectFixture = projectFixture()

    @Test
    fun `stress and limits test`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking(15.minutes) {
        val service = projectFixture.get().service<ProcessOutputControllerService>()
        val newLineLen = if (OS.CURRENT == OS.Windows) 2 else 1
        val binOnEel = BinOnEel(python, cwd)
        val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

        fun verifyCurrentProcesses(over: Int) {
            var count = 0

            for (process in service.loggedProcesses.value) {
                if (process.lines.isEmpty()
                    || process.lines[0].kind != OutputKindDto.OUT
                    || !process.lines[0].text.startsWith("test ")) {
                    continue
                }

                with(process.lines) {
                    assertEquals(3, size)
                    assert(get(0).text.split(" ")[1].toInt() >= over)

                    val xLen =
                        LoggingLimits.MAX_OUTPUT_SIZE - (get(0).text.length + newLineLen)
                    val yLen = LoggingLimits.MAX_OUTPUT_SIZE

                    assertNotNull(
                        find { elem ->
                            elem.text.startsWith("xxx") && elem.text.length == xLen
                        },
                    )
                    assertNotNull(
                        find { elem ->
                            elem.text.startsWith("yyy") && elem.text.length == yLen
                        },
                    )
                }

                count++
            }

            // should expect to have found and asserted MAX_PROCESSES amount processes
            // 10 for margin of error
            assert(count > ProcessOutputControllerServiceLimits.MAX_PROCESSES - 10)
        }

        edtWriteAction {
            mainPy.toFile().writeText(
                """
                    import sys 
                    
                    print("test " + sys.argv[1])
                    print("${"x".repeat(LoggingLimits.MAX_OUTPUT_SIZE * 2)}")
                    print("${"y".repeat(LoggingLimits.MAX_OUTPUT_SIZE * 2)}", file=sys.stderr)
                """.trimIndent(),
            )
        }

        // executing the file MAX_PROCESSES amount of times
        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
            runBin(binOnEel, Args(MAIN_PY, it.toString()))
        }

        // the amount of processes logged should exactly equal to MAX_PROCESSES
        waitUntil {
            service.loggedProcesses.value.size == ProcessOutputControllerServiceLimits.MAX_PROCESSES
        }

        // should have verified processes 0 to MAX_PROCESSES - 1
        verifyCurrentProcesses(0)

        // adding processes 2 times over the limit
        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2) {
            val newIt = (it + ProcessOutputControllerServiceLimits.MAX_PROCESSES)

            runBin(binOnEel, Args(MAIN_PY, newIt.toString()))
        }

        // older processes beyond MAX_PROCESSES should be truncated
        assertEquals(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
            service.loggedProcesses.value.size,
        )

        // should have verified processes MAX_PROCESSES to MAX_PROCESSES * 2 - 1
        verifyCurrentProcesses(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2)
    }

    @Test
    fun `exit info collector coroutines get properly cleaned up`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking(15.minutes) {
        projectFixture.get().service<ProcessOutputControllerService>()

        val binOnEel = BinOnEel(python, cwd)
        val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

        edtWriteAction {
            mainPy.toFile().writeText(
                """
                    import sys 
                    
                    print("test " + sys.argv[1])
                    sys.stdin.read(1)
                """.trimIndent(),
            )
        }

        // no coroutines should be active (5 for margin of error)
        assert(exitInfoCollectorCoroutinesCount() < 5)

        // spawn 1024 processes, instantly terminate them
        repeat(1024) {
            val process = runBinWithInput(binOnEel, Args(MAIN_PY, it.toString()))
            inputAndAwaitExit(process)
        }

        // no coroutines should be active (5 for margin of error)
        waitUntil {
            exitInfoCollectorCoroutinesCount() < 5
        }

        // spawn 100 processes
        val processes = mutableListOf<Process>()
        repeat(100) {
            processes += runBinWithInput(binOnEel, Args(MAIN_PY, it.toString()))
        }

        // at least 100 coroutines should be active
        waitUntil {
            exitInfoCollectorCoroutinesCount() >= 100
        }

        // but not more than 105
        assert(exitInfoCollectorCoroutinesCount() <= 105)

        // terminating all processes
        for (process in processes) {
            inputAndAwaitExit(process)
        }

        // updating the flow by adding and terminating one process
        val process = runBinWithInput(binOnEel, Args(MAIN_PY, "500"))
        inputAndAwaitExit(process)

        // no coroutines should be active (5 for margin of error)
        waitUntil {
            exitInfoCollectorCoroutinesCount() < 5
        }
    }

    @Test
    fun `tag section and exit info copy buttons test`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking {
        val service = projectFixture.get().service<ProcessOutputControllerService>()

        val binOnEel = BinOnEel(python, cwd)
        val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

        edtWriteAction {
            mainPy.toFile().writeText(
                """
                    import sys 
                    
                    print("out1")
                    print("out2")
                    print("out3")
                    print("out4")
                    print("out5")
                    print("out6")
                    
                    print("err7", file=sys.stderr)
                    print("err8", file=sys.stderr)
                    print("err9", file=sys.stderr)
                    print("err10", file=sys.stderr)
                """.trimIndent(),
            )
        }

        val loggingProcess = withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
            ExecService().executeGetProcess(
                binOnEel,
                Args(MAIN_PY),
                CoroutineScope(coroutineContext),
            ).getOrThrow()
        }

        // reading all stdout
        loggingProcess.inputStream.readAllBytes()

        waitUntil {
            service.loggedProcesses.value.lastOrNull()?.lines?.size == 6
        }

        val process = service.loggedProcesses.value.last()

        // stdout section (0..5)
        service.copyOutputTagAtIndexToClipboard(process, 0)

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

        // reading all stderr
        loggingProcess.errorStream.readAllBytes()

        waitUntil { process.lines.size == 10 }

        // stderr section (6..9)
        service.copyOutputTagAtIndexToClipboard(process, 6)

        assertEquals(
            """
                err7
                err8
                err9
                err10
                
            """.trimIndent(),
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
        )

        // exit info without additional message
        service.copyOutputExitInfoToClipboard(process)

        assertEquals(
            """
                0
                
            """.trimIndent(),
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
        )

        // exit info with additional message
        val status = process.status.value as ProcessStatus.Done

        (process.status as MutableStateFlow<ProcessStatus>).emit(
            status.copy(
                additionalMessageToUser = "some test message",
            ),
        )

        service.copyOutputExitInfoToClipboard(process)

        assertEquals(
            """
                0: some test message
                
            """.trimIndent(),
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
        )
    }

    @Test
    fun `toolbar copy includes tags depending on whether the filter is enabled`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking {
        val service = projectFixture.get().service<ProcessOutputControllerService>()

        val binOnEel = BinOnEel(python, cwd)
        val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

        edtWriteAction {
            mainPy.toFile().writeText(
                """
                    import sys 
                    
                    print("out1")
                    print("out2")
                    print("out3")
                    print("out4")
                    print("out5")
                    print("out6")
                    
                    print("err7", file=sys.stderr)
                    print("err8", file=sys.stderr)
                    print("err9", file=sys.stderr)
                    print("err10", file=sys.stderr)
                """.trimIndent(),
            )
        }

        val loggingProcess = withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
            ExecService().executeGetProcess(
                binOnEel,
                Args(MAIN_PY),
                CoroutineScope(coroutineContext),
            ).getOrThrow()
        }

        // reading all stdout
        loggingProcess.inputStream.readAllBytes()

        waitUntil {
            service.loggedProcesses.value.lastOrNull()?.lines?.size == 6
        }

        val process = service.loggedProcesses.value.last()

        // reading all stderr
        loggingProcess.errorStream.readAllBytes()

        waitUntil {
            service.loggedProcesses.value.lastOrNull()?.lines?.size == 10
        }

        // copying output
        service.copyOutputToClipboard(process)

        // copied output should include tags
        assertEquals(
            """
                [stdout] out1
                         out2
                         out3
                         out4
                         out5
                         out6
                [stderr] err7
                         err8
                         err9
                         err10
                  [exit] 0
                
            """.trimIndent(),
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
        )

        // toggling the show tags filter
        service.processOutputUiState.filters.active.toggle(OutputFilter.Item.SHOW_TAGS)
        service.copyOutputToClipboard(process)

        // copied output should not include tags
        waitUntil("output without tags") {
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) ==
                """
                    out1
                    out2
                    out3
                    out4
                    out5
                    out6
                    err7
                    err8
                    err9
                    err10
                    0
                    
                """.trimIndent()
        }
    }

    @Test
    fun `non-ascii output lines are reflected properly`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking {
        val service = projectFixture.get().service<ProcessOutputControllerService>()

        val binOnEel = BinOnEel(python, cwd)
        val mainPy = Files.createFile(cwd.resolve(MAIN_PY))
        val testTag = "non-ascii test"
        val nonAsciiText = "Привет, Мир"

        edtWriteAction {
            mainPy.toFile().writeText(
                """
                    import sys 
                    
                    sys.stdout.buffer.write("$testTag\n".encode("utf8"))
                    sys.stdout.buffer.write("$nonAsciiText\n".encode("utf8"))
                """.trimIndent(),
            )
        }

        runBin(binOnEel, Args(MAIN_PY))
        var lines: List<OutputLineDto>? = null

        waitUntil {
            service.loggedProcesses.value
                .lastOrNull()
                ?.lines
                ?.also {
                    lines = it
                }
                ?.firstOrNull()
                ?.text == testTag
        }

        assertEquals(nonAsciiText, lines?.last()?.text)
    }

    private fun Map<Int, LoggedProcess>.remapByFirstLine(): Map<String?, LoggedProcess> =
        mapOf(
            *toList()
                .map { (_, process) ->
                    process.lines.firstOrNull()?.text to process
                }
                .toTypedArray(),
        )

    companion object {
        const val MAIN_PY = "main.py"

        suspend fun runBinWithInput(binOnEel: BinOnEel, args: Args): Process =
            ExecService().executeGetProcess(
                binOnEel,
                args,
                CoroutineScope(NON_INTERACTIVE_ROOT_TRACE_CONTEXT),
            ).getOrThrow()

        suspend fun inputAndAwaitExit(process: Process) {
            process.outputStream.write(0)
            process.outputStream.flush()

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
        }

        suspend fun runBin(binOnEel: BinOnEel, args: Args) {
            withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
                val process = ExecService().executeGetProcess(
                    binOnEel,
                    args,
                    CoroutineScope(coroutineContext),
                ).orThrow()

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
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun exitInfoCollectorCoroutinesCount(): Int =
            DebugProbes.dumpCoroutinesInfo()
                .filter { it.context[CoroutineName.Key]?.name == CoroutineNames.EXIT_INFO_COLLECTOR }
                .size
    }
}
