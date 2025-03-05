package com.intellij.python.junit5Tests.framework

import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonHomePath
import kotlinx.coroutines.delay
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
fun PythonHomePath.resolvePythonTool(name: String): Path = when (getEelDescriptor().operatingSystem) {
  EelPath.OS.WINDOWS -> resolve("Scripts/$name.exe")
  EelPath.OS.UNIX -> resolve("bin/$name")
}
