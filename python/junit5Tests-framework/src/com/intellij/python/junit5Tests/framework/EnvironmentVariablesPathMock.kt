// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.util.EnvironmentUtil
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Extension on top of [EnvironmentVariables] to add [pathsToAdd] to the [PATH].
 * Call it before the test. After the test please call [unMockPath].
 * See [com.intellij.python.junit5Tests.unit.alsoWin.showCase.EnvironmentVariablesPathMockTest].
 *
 * It mocks both [System.getenv] and [EnvironmentUtil] used by IJ (latter might cache something).
 */
fun EnvironmentVariables.mockPathAndAdd(vararg pathsToAdd: Path) {
  // TODO: Use native calls to `SetEnvironmentVariable` and `setenv(3)` to change env for children processes (they inherit parent envs).
  val (pathKey, paths) = variables.entries.find { it.key.isPath }?.toPair()
                         ?: Pair(PATH, "")
  val newPathVal = (paths.split(File.pathSeparator) + pathsToAdd.map { it.pathString }).joinToString(File.pathSeparator)
  set(pathKey, newPathVal) // Mock System.env
  mockkStatic(EnvironmentUtil::class) // Mock EnvironmentUtil
  every { EnvironmentUtil.getValue(any()) }.coAnswers {
    if ((it.invocation.args[0] as String).isPath) newPathVal else it.invocation.originalCall.invoke() as String
  }
}

/**
 * See [mockPathAndAdd]
 */
fun EnvironmentVariables.unMockPath() {
  unmockkStatic(EnvironmentUtil::class)
}

private const val PATH = "PATH"
private val String.isPath: Boolean get() = uppercase() == PATH