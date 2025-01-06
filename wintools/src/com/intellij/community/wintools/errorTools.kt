package com.intellij.community.wintools

import com.intellij.openapi.util.NlsSafe
import com.sun.jna.platform.win32.Kernel32

/**
 * @return last `Windows error as a pretty printed code
 */
fun kernel32LastError(): String {
  val lastError = Kernel32.INSTANCE.GetLastError()
  return prettyHRESULT(lastError)
}


fun prettyHRESULT(lastError: Int): String {
  val hResult = if (lastError <= 0) {
    lastError
  }
  else {
    (lastError and 0x0000FFFF) or (7 shl 16) or 0x80000000.toInt()
  }

  return String.format("0x%08X", hResult)
}

// impl
internal fun <T> winFailure(message: @NlsSafe String): Result<T> = Result.failure(Win32Exception(message))
private class Win32Exception(message: @NlsSafe String) : Exception("$message: ${kernel32LastError()}")
