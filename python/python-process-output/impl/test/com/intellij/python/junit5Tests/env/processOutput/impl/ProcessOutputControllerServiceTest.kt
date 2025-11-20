package com.intellij.python.junit5Tests.env.processOutput.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.processOutput.impl.ProcessOutputControllerService
import com.intellij.python.processOutput.impl.ProcessOutputControllerServiceLimits
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.progress.sleepCancellable
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class ProcessOutputControllerServiceTest {
    private val projectFixture = projectFixture()

    @Test
    fun `stress and limits test`(
        @TempDir cwd: Path,
        @PythonBinaryPath python: PythonBinary,
    ): Unit = timeoutRunBlocking(5.minutes) {
        val service = projectFixture.get().service<ProcessOutputControllerService>()

        val binOnEel = BinOnEel(python, cwd)
        val mainPyFileName = "main.py"
        val mainPy = Files.createFile(cwd.resolve(mainPyFileName))

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
            runBin(binOnEel, Args(mainPyFileName, it.toString()))
        }

        // let the service catch up with the flows
        sleepCancellable(200)

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
                    LoggingLimits.MAX_OUTPUT_SIZE - ("test $it".length + 1),
                    get(1).text.length,
                )
                assertEquals(LoggingLimits.MAX_OUTPUT_SIZE, get(2).text.length)
            }
        }

        // adding processes 2 times over the limit
        repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2) {
            runBin(
                binOnEel,
                Args(
                    mainPyFileName,
                    (it + ProcessOutputControllerServiceLimits.MAX_PROCESSES).toString(),
                ),
            )
        }

        // catching up
        sleepCancellable(200)

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
                    LoggingLimits.MAX_OUTPUT_SIZE - ("test $newIt".length + 1),
                    get(1).text.length,
                )
                assertEquals(LoggingLimits.MAX_OUTPUT_SIZE, get(2).text.length)
            }
        }
    }

    companion object {
        suspend fun runBin(binOnEel: BinOnEel, args: Args) {
            val process = ExecService().executeGetProcess(binOnEel, args).orThrow()

            coroutineScope {
                listOf(
                    async(Dispatchers.IO) {
                        process.errorStream.readAllBytes()
                    },
                    async(Dispatchers.IO) {
                        process.inputStream.readAllBytes()
                    },
                ).awaitAll()
            }
        }
    }
}
