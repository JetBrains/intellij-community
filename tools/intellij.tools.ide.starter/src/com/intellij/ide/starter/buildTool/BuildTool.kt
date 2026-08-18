package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.KILL_THREAD_DUMP_FILE_PREFIX
import com.intellij.ide.starter.process.collectJavaThreadDumpSuspendable
import com.intellij.ide.starter.process.getProcessesIdByProcessName
import com.intellij.ide.starter.utils.ReportingPathUtils.checkPathLength
import com.intellij.ide.starter.utils.ReportingPathUtils.shortenFileStemIn
import com.intellij.ide.starter.utils.catchAll
import java.nio.file.Path

/**
 * Stuff related to particular build tool
 */
abstract class BuildTool(val type: BuildToolType, val testContext: IDETestContext) {
  suspend fun collectDumpFile(processName: String, logsDir: Path, jdkHome: Path, workDir: Path) {
    catchAll {
      getProcessesIdByProcessName(processName).forEachIndexed { index, processId ->
        val fileStem = shortenFileStemIn(
          logsDir,
          "$KILL_THREAD_DUMP_FILE_PREFIX-${System.currentTimeMillis()}-$processName-$index",
          extension = ".txt",
          preservedPrefix = KILL_THREAD_DUMP_FILE_PREFIX,
        )
        val dumpFile = checkPathLength(logsDir.resolve("$fileStem.txt"))
        collectJavaThreadDumpSuspendable(jdkHome, workDir, processId, dumpFile)
      }
    }
  }
}
