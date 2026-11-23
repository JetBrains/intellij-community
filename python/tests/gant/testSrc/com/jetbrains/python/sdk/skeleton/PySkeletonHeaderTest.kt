// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.skeleton

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A [LightVirtualFile] has no NIO path, so these tests show that the VFS read of a header needs none. */
@TestFor(classes = [PySkeletonHeader::class], issues = ["PY-93900"])
@Subsystems.Interpreters
@Layers.Functional
class PySkeletonHeaderTest : PyCodeInsightTestCase() {
  private fun read(text: String): PySkeletonHeader? = PySkeletonHeader.readSkeletonHeader(LightVirtualFile("_ssl.py", text))

  @Test
  fun `a header in the current format names the binary and the generator version`() {
    val header = read(
      """
      # encoding: utf-8
      # module _ssl
      # from /usr/lib/python3.12/lib-dynload/_ssl.cpython-312-x86_64-linux-gnu.so
      # by generator 1.147

      """.trimIndent()
    )!!
    assertEquals("/usr/lib/python3.12/lib-dynload/_ssl.cpython-312-x86_64-linux-gnu.so", header.binaryFile)
    assertEquals((1 shl 8) + 147, header.version)
  }

  @Test
  fun `a header in the old one-line format names the binary and the generator version`() {
    val header = read(
      """
      # encoding: utf-8
      # module _ssl
      # from /usr/lib/_ssl.so by generator 1.2

      """.trimIndent()
    )!!
    assertEquals("/usr/lib/_ssl.so", header.binaryFile)
    assertEquals((1 shl 8) + 2, header.version)
  }

  @Test
  fun `a header of a built-in module names no binary`() {
    val header = read(
      """
      # encoding: utf-8
      # module sys
      # from (built-in)
      # by generator 1.147

      """.trimIndent()
    )!!
    assertEquals(PySkeletonHeader.BUILTIN_NAME, header.binaryFile)
  }

  @Test
  fun `a file without a header has no header`() {
    assertNull(read(
      """
      import os

      x = 1
      y = 2

      """.trimIndent()
    ))
  }
}
