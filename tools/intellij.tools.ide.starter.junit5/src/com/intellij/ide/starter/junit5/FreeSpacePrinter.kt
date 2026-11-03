package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.getDiskInfo
import com.intellij.ide.starter.utils.withIndent
import com.intellij.tools.ide.util.common.logOutput
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import java.nio.file.Files

/**
 * Below this usable space, the listener also prints the sizes of the test directories.
 * The sizes walk the whole test home, which takes minutes when the test projects are large.
 */
private const val DIRECTORY_SIZES_BELOW_USABLE_GB: Long = 50

/**
 * The listener prints free space available to the test, and the sizes of the test directories when the space is low
 */
open class FreeSpacePrinter : TestExecutionListener {

  override fun executionStarted(testIdentifier: TestIdentifier) {
    if (testIdentifier?.isTest != true) {
      return
    }

    if (CIServer.instance.isBuildRunningOnCI) {
      val fileStore = Files.getFileStore(GlobalPaths.instance.testHomePath)
      val isSpaceLow = fileStore.usableSpace < DIRECTORY_SIZES_BELOW_USABLE_GB * 1024 * 1024 * 1024
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testIdentifier.displayName}")
        if (isSpaceLow) {
          appendLine(FileSystem.getDiskUsageDiagnostics().withIndent("  "))
        }
        else {
          appendLine(fileStore.getDiskInfo().withIndent("  "))
          appendLine("  The directory sizes are skipped: the usable space is above $DIRECTORY_SIZES_BELOW_USABLE_GB GB")
        }
      })
    }
  }
}