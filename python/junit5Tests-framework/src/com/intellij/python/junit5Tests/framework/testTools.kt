// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonHomePath
import kotlinx.coroutines.delay
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import java.io.IOException
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


/**
 * Awaits for [kotlin.code] (i.e [org.junit.jupiter.api.Assertions.assertEquals]) doesn't throw [kotlin.AssertionError]
 */
suspend fun waitNoError(delay: Duration = 100.milliseconds, repeat: Int = 50, checkCondition: suspend () -> Unit) {
  repeat(repeat) {
    try {
      checkCondition()
      return
    }
    catch (_: AssertionError) {
      false
      delay(delay)
    }
  }
  checkCondition()
}

@RequiresBackgroundThread
fun PythonHomePath.resolvePythonTool(name: String): Path = when (getEelDescriptor().osFamily) {
  EelOsFamily.Windows -> resolve("Scripts/$name.exe")
  EelOsFamily.Posix-> resolve("bin/$name")
}


/**
 * Hamcrest [Matcher] for [kotlin.io.path.Path] to check if path starts with (aka is child of) [parent]
 */
fun startsWith(parent: Path): Matcher<Path> = PathMatcher(parent)

private class PathMatcher(private val parent: Path) : TypeSafeMatcher<Path>(Path::class.java) {
  override fun matchesSafely(item: Path?): Boolean = item != null && item.expandWinPath().startsWith(parent.expandWinPath())

  override fun describeTo(description: Description) {
    description.appendText("Starts with $parent")
  }

  private fun Path.expandWinPath(): Path =
    try {
      when (getEelDescriptor().osFamily) {
        // On Windows we change 8.3 problem (c:\users\William.~1 -> c:\users\William.Gates)
        // But you are encountered to disable 8.3 with `fsutil 8dot3name set 1`
        EelOsFamily.Windows -> toRealPath()
        // On Unix, this function resolves symlinks (i.e `~/.venv/python` -> `/usr/bin/python`) which isn't what we want.
        EelOsFamily.Posix -> this
      }
    }
    catch (_: IOException) {
      this
    }
}
