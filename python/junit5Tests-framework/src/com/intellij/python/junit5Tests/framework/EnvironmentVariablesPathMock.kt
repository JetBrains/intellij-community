// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.util.EnvironmentUtil
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.io.File
import java.nio.file.Path
import java.util.function.Supplier
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
  val oldVars = HashMap(variables)
  val pathKey = variables.keys.firstOrNull { it.isPath } ?: PATH
  val paths = variables[pathKey] ?: ""

  val newPathVal = (paths.split(File.pathSeparator) + pathsToAdd.map { it.pathString }).joinToString(File.pathSeparator)
  set(pathKey, newPathVal) // Mock System.env
  val currentEnvs = HashMap(variables)
  currentEnvs[pathKey] = newPathVal
  currentEnvs[PATH] = newPathVal
  EnvironmentUtil.setEnvironmentLoader(MyMap(new = currentEnvs, old = oldVars))
}

/**
 * See [mockPathAndAdd]
 */
fun EnvironmentVariables.unMockPath() {
  val original = HashMap((EnvironmentUtil.getEnvironmentMap() as MyMap).old)
  EnvironmentUtil.setEnvironmentLoader { original }
}

private const val PATH = "PATH"
private val String.isPath: Boolean get() = uppercase() == PATH

private class MyMap(private val new: Map<String, String>, val old: Map<String, String>) : Supplier<Map<String, String>>,
                                                                                          Map<String, String> by new {
  override fun get(): Map<String, String> = this
}