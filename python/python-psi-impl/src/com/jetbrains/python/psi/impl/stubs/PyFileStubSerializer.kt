// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.stubs.StubSerializer
import com.intellij.psi.tree.IFileElementType
import com.jetbrains.python.psi.PyFileElementType
import com.jetbrains.python.psi.stubs.PyFileStub
import java.util.BitSet

/**
 * Serializes the file stub of a Python file whose file node type is a plain [IFileElementType].
 * [externalId] stays the id of the former `IStubFileElementType` of the language, so the stored stubs keep their ids.
 * A dialect with its own [PyFileElementType] delegates to [write] and [read], so the format has one home.
 */
internal class PyFileStubSerializer(private val fileElementType: IFileElementType, private val externalId: String) :
  StubSerializer<PyFileStub> {
  override fun getExternalId(): String = externalId

  override fun serialize(stub: PyFileStub, dataStream: StubOutputStream): Unit = write(stub, dataStream)

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): PyFileStub = read(dataStream, fileElementType)

  override fun indexStub(stub: PyFileStub, sink: IndexSink) {
  }

  companion object {
    /** The [FutureFeature][com.jetbrains.python.psi.FutureFeature] ids fit into an `int`, see [PyFileStubImpl]. */
    private const val FUTURE_FEATURE_BITS = 32

    @JvmStatic
    fun write(stub: PyFileStub, dataStream: StubOutputStream) {
      PyFileElementType.writeNullableList(dataStream, stub.dunderAll)
      writeBitSet(dataStream, stub.futureFeatures)
      dataStream.writeName(stub.deprecationMessage)
    }

    /** Reads a file stub whose file node type is [fileElementType]. */
    @JvmStatic
    fun read(dataStream: StubInputStream, fileElementType: IFileElementType): PyFileStub {
      val all = PyFileElementType.readNullableList(dataStream)
      val futureFeatures = readBitSet(dataStream)
      val deprecationMessage = dataStream.readName()
      return PyFileStubImpl(all, futureFeatures, deprecationMessage, fileElementType)
    }

    private fun readBitSet(dataStream: StubInputStream): BitSet {
      val result = BitSet(FUTURE_FEATURE_BITS)
      val bits = dataStream.readInt()
      for (i in 0 until FUTURE_FEATURE_BITS) {
        result.set(i, bits and (1 shl i) != 0)
      }
      return result
    }

    private fun writeBitSet(dataStream: StubOutputStream, bitSet: BitSet) {
      var result = 0
      for (i in 0 until FUTURE_FEATURE_BITS) {
        if (bitSet.get(i)) {
          result = result or (1 shl i)
        }
      }
      dataStream.writeInt(result)
    }
  }
}
