// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.AbstractStringEnumerator
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException

/**
 * Writes a custom stub as `[name count][names][byte length][bytes]`, so that a reader with no extension for its type can
 * skip it. The custom stub writes into its own stream, and the names of that stream go to the enclosing stream.
 */
internal object PyCustomStubFormat {
  @JvmStatic
  @Throws(IOException::class)
  fun write(stub: PyCustomStub, stream: StubOutputStream) {
    val names = WrittenNames()
    val bytes = BufferExposingByteArrayOutputStream()
    stub.serialize(StubOutputStream(bytes, names))
    stream.writeVarInt(names.values.size)
    names.values.forEach { stream.writeName(it) }
    stream.writeVarInt(bytes.size())
    stream.write(bytes.internalBuffer, 0, bytes.size())
  }

  /** Reads the names and the bytes of one custom stub. [Body.read] then reads the stub, or the caller skips it. */
  @JvmStatic
  @Throws(IOException::class)
  fun read(stream: StubInputStream): Body {
    val names = Array(stream.readVarInt()) { stream.readNameString() }
    val size = stream.readVarInt()
    if (size < 0) throw IOException("A custom stub has a negative byte length $size")
    val bytes = stream.readNBytes(size)
    if (bytes.size != size) throw EOFException("A custom stub has ${bytes.size} of its $size bytes")
    return Body(ReadNames(names), bytes)
  }

  class Body internal constructor(private val names: AbstractStringEnumerator, private val bytes: ByteArray) {
    /**
     * Reads the stub with [type]. A stub that leaves bytes unread is an error, because the type then reads a different
     * format than its stub wrote. A type that returns `null` can stop early.
     */
    @Throws(IOException::class)
    fun <Stub : PyCustomStub> read(type: PyCustomStubType<*, out Stub>): Stub? {
      val input = ByteArrayInputStream(bytes)
      val stub = try {
        type.deserializeStub(StubInputStream(input, names))
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        throw IOException("Custom stub type ${type.javaClass.canonicalName} failed to read its ${bytes.size} bytes", e)
      }
      if (stub != null && input.available() != 0) {
        throw IOException("Custom stub type ${type.javaClass.canonicalName} left ${input.available()} of its ${bytes.size} bytes")
      }
      return stub
    }
  }

  /**
   * The name storage for the stream of one custom stub.
   *
   * [write] serializes the stub into a buffer first, because the byte length must come before the bytes. A
   * [StubOutputStream] on that buffer needs a name storage. The stub types write their names with
   * [StubOutputStream.writeName], which writes the index that the storage gives. The enclosing stream keeps its name
   * storage private, so the custom stub cannot use it. Thus this class gives each new name the next index, from 1. The
   * index of `null` is 0, as in [StubOutputStream.writeName]. The stub bytes contain these indexes, not the names.
   *
   * [write] then writes the names before the bytes, with `writeName` of the enclosing stream. Thus each name is stored
   * once per file, also when other stubs of the file use it. Raw strings in the bytes would store a name again at each
   * use. The names are outside the bytes, so a reader with no extension for the stub type can read them and skip the
   * bytes. [ReadNames] maps the indexes in the bytes back to the names.
   */
  private class WrittenNames : NoStorage() {
    private val ids = HashMap<String, Int>()
    val values = ArrayList<String?>()

    override fun enumerate(value: String?): Int {
      if (value == null) return 0
      return ids.getOrPut(value) {
        values.add(value)
        values.size
      }
    }

    override fun valueOf(idx: Int): String? = if (idx == 0) null else values[idx - 1]
  }

  /**
   * The names of one custom stub, as [read] reads them. Index `i` gives `values[i - 1]`, and index 0 gives `null`. A
   * `StringRef` from [StubInputStream.readName] keeps this storage and gets its name only when it is used.
   */
  private class ReadNames(private val values: Array<String?>) : NoStorage() {
    override fun enumerate(value: String?): Int = throw UnsupportedOperationException("A read custom stub has no new names")

    override fun valueOf(idx: Int): String? {
      if (idx == 0) return null
      if (idx !in 1..values.size) throw IOException("A custom stub has no name $idx of ${values.size}")
      return values[idx - 1]
    }
  }

  private abstract class NoStorage : AbstractStringEnumerator {
    override fun markCorrupted() {}

    override fun close() {}

    override fun isDirty(): Boolean = false

    override fun force() {}
  }
}
