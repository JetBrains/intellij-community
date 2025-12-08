package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals

@PyEnvTestCase
class ProcessOutputControllerServiceTest {
    private val projectFixture = projectFixture()

    @Test
    fun `stress and limits test`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking(15.minutes) {
        val service = projectFixture.get().service<ProcessOutputControllerService>()

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

            waitUntil {
                service.firstLineOfLastProcess()?.startsWith("test $it") == true
            }
        }

        // the amount of processes logged should exactly equal to MAX_PROCESSES
        assertEquals(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
            service.loggedProcesses.value.size,
        )

        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
            with(service.loggedProcesses.value[it].lines.replayCache) {
                // line 1 (stdout): test $it
                // line 2 (stdout): x repeated MAX_OUTPUT_SIZE times minus the length of "test $it" + 1
                // line 3 (stderr): y repeated MAX_OUTPUT_SIZE times
                assertEquals(3, size)
                assertEquals("test $it", get(0).text)
                assertEquals(
                    LoggingLimits.MAX_OUTPUT_SIZE - ("test $it".length + newLineLen),
                    get(1).text.length,
                )
                assertEquals(LoggingLimits.MAX_OUTPUT_SIZE, get(2).text.length)
            }
        }

        // adding processes 2 times over the limit
        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2) {
            val newIt = (it + ProcessOutputControllerServiceLimits.MAX_PROCESSES)

            runBin(binOnEel, Args(MAIN_PY, newIt.toString()))

            waitUntil {
                service.firstLineOfLastProcess()?.startsWith("test $newIt") == true
            }
        }

        // older processes beyond MAX_PROCESSES should be truncated
        assertEquals(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
            service.loggedProcesses.value.size,
        )

        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
            with(service.loggedProcesses.value[it].lines.replayCache) {
                val newIt = it + ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2

                // line 1 (stdout): test $newIt
                // line 2 (stdout): x repeated MAX_OUTPUT_SIZE times minus the length of "test $newIt" + 1
                // line 3 (stderr): y repeated MAX_OUTPUT_SIZE times
                assertEquals(3, size)
                assertEquals("test $newIt", get(0).text)
                assertEquals(
                    LoggingLimits.MAX_OUTPUT_SIZE - ("test $newIt".length + newLineLen),
                    get(1).text.length,
                )
                assertEquals(LoggingLimits.MAX_OUTPUT_SIZE, get(2).text.length)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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
                """.trimIndent(),
            )
        }

        // no exit info collector coroutines should exist
        assertEquals(
            0,
            DebugProbes.dumpCoroutinesInfo()
                .filter { it.context[CoroutineName.Key]?.name == CoroutineNames.EXIT_INFO_COLLECTOR }
                .size,
        )

        // spawn 1024 processes
        repeat(1024) {
            runBin(binOnEel, Args(MAIN_PY, it.toString()))
        }

        sleepCancellable(1000)

        // the count of active exit info collector coroutines should match MAX_PROCESSES
        assertEquals(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
            DebugProbes.dumpCoroutinesInfo()
                .filter { it.context[CoroutineName.Key]?.name == CoroutineNames.EXIT_INFO_COLLECTOR }
                .size,
        )
    }

    private fun ProcessOutputControllerService.firstLineOfLastProcess(): String? =
        loggedProcesses
            .value
            .lastOrNull()
            ?.lines
            ?.replayCache
            ?.getOrNull(0)
            ?.text

    companion object {
        const val MAIN_PY = "main.py"

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
    }
}
