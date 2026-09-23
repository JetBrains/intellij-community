package com.intellij.ide.starter.testContextCache.utils

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.hyphenateTestName

/**
 * Builds the directory name of a current test context created from a cached one: [baseName], with the IDE run
 * mode before it.
 *
 * Both ways to reuse a prepared project name a context by the test class. The test itself gets its directory from
 * [com.intellij.ide.starter.runner.IDEReportingData], below the test home.
 *
 * The mode is left out when [baseName] names it already. Then two classes whose names differ only in that word share
 * a directory.
 */
fun testContextDirName(runMode: IdeRunMode, baseName: String): String {
  val modeName = when (runMode) {
    IdeRunMode.MONOLITH -> null
    IdeRunMode.SPLIT -> "split"
  }
  val hyphenatedBaseName = baseName.hyphenateTestName()
  if (modeName == null ||
      hyphenatedBaseName == modeName ||
      hyphenatedBaseName.startsWith("$modeName-") ||
      hyphenatedBaseName.endsWith("-$modeName")) {
    return ReportingPathUtils.dirName(hyphenatedBaseName)
  }
  return ReportingPathUtils.dirName(hyphenatedBaseName, prefix = "$modeName-")
}

/** Names the context of the running test by its test class, see [testContextDirName]. */
internal fun currentTestContextDirName(): String {
  val testMethod = checkNotNull(CurrentTestMethod.get()) { "A cached project test context belongs to a test, and no test is running" }
  return testContextDirName(IdeRunMode.current(), testMethod.clazzSimpleName)
}
