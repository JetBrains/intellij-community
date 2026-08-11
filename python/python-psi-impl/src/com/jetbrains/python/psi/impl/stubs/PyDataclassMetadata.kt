// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.DataInputOutputUtil
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.IOException

/**
 * An opaque, framework-specific payload persisted in a dataclass class stub ([com.jetbrains.python.psi.stubs.PyDataclassStub]) or field stub
 * ([com.jetbrains.python.psi.stubs.PyDataclassFieldStub]).
 */
@ApiStatus.Internal
class PyDataclassMetadata private constructor(private val bytes: ByteArray) {

  /**
   * Decodes the payload with the framework's own reader, or returns `null` if the payload could not be read
   * (truncated or written by an incompatible version).
   */
  fun <T : Any> decode(reader: (DataInput) -> T): T? =
    try {
      DataInputStream(ByteArrayInputStream(bytes)).use(reader)
    }
    catch (_: IOException) {
      null
    }

  override fun toString(): String = "PyDataclassMetadata(${bytes.size} bytes)"

  companion object {
    fun encode(writer: (DataOutput) -> Unit): PyDataclassMetadata {
      val buffer = ByteArrayOutputStream()
      DataOutputStream(buffer).use(writer)
      return PyDataclassMetadata(buffer.toByteArray())
    }

    @Throws(IOException::class)
    fun readFrom(stream: StubInputStream): PyDataclassMetadata? =
      DataInputOutputUtil.readNullable(stream) {
        val bytes = ByteArray(DataInputOutputUtil.readINT(stream))
        stream.readFully(bytes)
        PyDataclassMetadata(bytes)
      }

    @Throws(IOException::class)
    fun writeTo(stream: StubOutputStream, metadata: PyDataclassMetadata?) {
      DataInputOutputUtil.writeNullable(stream, metadata) {
        DataInputOutputUtil.writeINT(stream, it.bytes.size)
        stream.write(it.bytes)
      }
    }
  }
}
