// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.intellij.util.io.DataInputOutputUtil
import com.jetbrains.python.codeInsight.PyDataclassParameters.PredefinedType
import com.jetbrains.python.codeInsight.parseDataclassParametersForStub
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyDataclassStub
import java.io.IOException

class PyDataclassStubType : PyCustomClassStubType<PyDataclassStub>() {

  override fun createStub(psi: PyClass): PyDataclassStub? {
    return PyDataclassStubImpl.create(psi)
  }

  @Throws(IOException::class)
  override fun deserializeStub(stream: StubInputStream): PyDataclassStub? {
    return PyDataclassStubImpl.deserialize(stream)
  }
}

class PyDataclassStubImpl(
  private val type: String,
  private val decoratorName: QualifiedName?,
  private val init: Boolean?,
  private val repr: Boolean?,
  private val eq: Boolean?,
  private val order: Boolean?,
  private val unsafeHash: Boolean?,
  private val frozen: Boolean?,
  private val matchArgs: Boolean?,
  private val kwOnly: Boolean?,
) : PyDataclassStub {

  companion object {
    val NON_PARAMETERIZED_DATACLASS_TRANSFORM_CANDIDATE_STUB = PyDataclassStubImpl(
      type = PredefinedType.DATACLASS_TRANSFORM.name,
      decoratorName = null,
      init = null,
      repr = null,
      eq = null,
      order = null,
      unsafeHash = null,
      frozen = null,
      matchArgs = null,
      kwOnly = null
    )

    fun create(cls: PyClass): PyDataclassStub? {
      return parseDataclassParametersForStub(cls)
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassStub? {
      val type = stream.readNameString() ?: return null
      val decoratorName = QualifiedName.deserialize(stream)
      val init = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val repr = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val eq = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val order = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val unsafeHash = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val frozen = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val matchArgs = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val kwOnly = DataInputOutputUtil.readNullable(stream, stream::readBoolean)

      return PyDataclassStubImpl(type, decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly)
    }
  }

  override fun getTypeClass(): Class<PyDataclassStubType> = PyDataclassStubType::class.java

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(type)
    QualifiedName.serialize(decoratorName, stream)
    DataInputOutputUtil.writeNullable(stream, init, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, repr, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, eq, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, order, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, unsafeHash, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, frozen, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, matchArgs, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, kwOnly, stream::writeBoolean)
  }

  override fun getType(): String = type
  override fun decoratorName(): QualifiedName? = decoratorName
  override fun initValue(): Boolean? = init
  override fun reprValue(): Boolean? = repr
  override fun eqValue(): Boolean? = eq
  override fun orderValue(): Boolean? = order
  override fun unsafeHashValue(): Boolean? = unsafeHash
  override fun frozenValue(): Boolean? = frozen
  override fun matchArgsValue(): Boolean? = matchArgs
  override fun kwOnly(): Boolean? = kwOnly
  
  override fun toString(): String {
    return "PyDataclassStub(" +
           "type='$type', " +
           "decoratorName=$decoratorName, " +
           "init=$init, " +
           "repr=$repr, " +
           "eq=$eq, " +
           "order=$order, " +
           "unsafeHash=$unsafeHash, " +
           "frozen=$frozen, " +
           "matchArgs=$matchArgs, " +
           "kwOnly=$kwOnly" +
           ")"
  }
}
