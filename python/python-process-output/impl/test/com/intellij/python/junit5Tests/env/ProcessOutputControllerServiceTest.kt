package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.impl.LoggedProcess
import com.intellij.python.community.execService.impl.LoggedProcessLine
import com.intellij.python.community.execService.impl.LoggedProcessLine.Kind.OUT
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.processOutput.impl.CoroutineNames
import com.intellij.python.processOutput.impl.ProcessOutputControllerService
import com.intellij.python.processOutput.impl.ProcessOutputControllerServiceLimits
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.io.awaitExit
import com.intellij.util.progress.sleepCancellable
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.launch
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
        val history = Collections.synchronizedMap(mutableMapOf<Int, LoggedProcess>())
        var historyUpdates = 0

        val watcher = launch {
            service.loggedProcesses.collect {
                historyUpdates += 1
                for (process in it) {
                    if (!history.contains(process.id)) {
                        history[process.id] = process
                    }
                }
            }
        }

        val newLineLen = if (SystemInfoRt.isWindows) 2 else 1
        val binOnEel = BinOnEel(python, cwd)
        val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

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

        waitUntil {
            val index = ProcessOutputControllerServiceLimits.MAX_PROCESSES - 1
            val process = history.toList().find { (_, it) ->
                it.lines.replayCache.find { it.text == "test $index" } != null
            }

            process != null && process.second.lines.replayCache.size == 3
        }

        // the amount of processes logged should exactly equal to MAX_PROCESSES
        assertEquals(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
            service.loggedProcesses.value.size,
        )
        assert(historyUpdates >= ProcessOutputControllerServiceLimits.MAX_PROCESSES)

        run {
            val processMap = history.remapByFirstLine()

            repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
                val process = processMap["test $it"]

                assertNotNull(process)

                with(process.lines.replayCache) {
                    // (stdout): test $it
                    // (stdout): x repeated MAX_OUTPUT_SIZE times minus the length of "test $it" + 1
                    // (stderr): y repeated MAX_OUTPUT_SIZE times
                    assertEquals(3, size)

                    val xLen = LoggingLimits.MAX_OUTPUT_SIZE - ("test $it".length + newLineLen)
                    val yLen = LoggingLimits.MAX_OUTPUT_SIZE

                    assertTrue(contains(LoggedProcessLine("test $it", OUT)))
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
            }
        }

        // adding processes 2 times over the limit
        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2) {
            val newIt = (it + ProcessOutputControllerServiceLimits.MAX_PROCESSES)

            runBin(binOnEel, Args(MAIN_PY, newIt.toString()))
        }

        waitUntil {
            val index = (ProcessOutputControllerServiceLimits.MAX_PROCESSES * 3) - 1
            val process = history.toList().find { (_, it) ->
                it.lines.replayCache.getOrNull(0)?.text == "test $index"
            }

            process != null && process.second.lines.replayCache.size == 3
        }

        // older processes beyond MAX_PROCESSES should be truncated
        assertEquals(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
            service.loggedProcesses.value.size,
        )
        assert(historyUpdates >= ProcessOutputControllerServiceLimits.MAX_PROCESSES * 3)

        run {
            val processMap = history.remapByFirstLine()

            repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
                val newIt = it + ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2
                val process = processMap["test $newIt"]

                assertNotNull(process)

                with(process.lines.replayCache) {
                    // (stdout): test $newIt
                    // (stdout): x repeated MAX_OUTPUT_SIZE times minus the length of "test $newIt" + 1
                    // (stderr): y repeated MAX_OUTPUT_SIZE times
                    val xLen = LoggingLimits.MAX_OUTPUT_SIZE - ("test $newIt".length + newLineLen)
                    val yLen = LoggingLimits.MAX_OUTPUT_SIZE

                    assertTrue(contains(LoggedProcessLine("test $newIt", OUT)))
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
            }
        }

        watcher.cancelAndJoin()
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

        // no exit info collector coroutines should exist
        assertEquals(0, exitInfoCollectorCoroutinesCount())

        // spawn 10 processes
        val processes = mutableListOf<Process>()
        repeat(10) {
            processes += runBinWithInput(binOnEel, Args(MAIN_PY, it.toString()))
        }

        // 10 collector coroutines should be active
        waitUntil {
            exitInfoCollectorCoroutinesCount() == 10
        }

        // spawn 1024 processes, instantly terminate them
        repeat(1024) {
            val process = runBinWithInput(binOnEel, Args(MAIN_PY, (it + 10).toString()))
            inputAndAwaitExit(process)
        }

        // 10 collection coroutines should be active
        waitUntil {
            exitInfoCollectorCoroutinesCount() == 10
        }

        // terminating all the processes
        for (process in processes) {
            inputAndAwaitExit(process)
        }

        // no collection coroutines should be active
        waitUntil {
            exitInfoCollectorCoroutinesCount() == 0
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
            service.loggedProcesses.value.lastOrNull()?.lines?.replayCache?.size == 6
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

        waitUntil { process.lines.replayCache.size == 10 }

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
        process.exitInfo.emit(
            process.exitInfo.value?.copy(additionalMessageToUser = "some test message"),
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
        var lines: List<LoggedProcessLine>? = null

        waitUntil {
            service.loggedProcesses.value
                .lastOrNull()
                ?.lines
                ?.replayCache
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
                    process.lines.replayCache.firstOrNull()?.text to process
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
