/*
 * Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.intellij.util.io.DataInputOutputUtil
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import java.io.IOException

/**
 * Implementation of [PyDataclassFieldStub] shared by every dataclass framework.
 *
 * Besides the field parameters common to every framework it persists the framework [type] (matching
 * [com.jetbrains.python.psi.stubs.PyDataclassStub.type] of the owning class) and an opaque [metadata] payload so a
 * framework can round-trip its own per-field configuration without core knowing the framework.
 */
class PyDataclassFieldStubImpl(
  private val type: String,
  private val calleeName: QualifiedName,
  private val hasDefault: Boolean,
  private val hasDefaultFactory: Boolean,
  private val initValue: Boolean,
  private val kwOnly: Boolean?,
  private val alias: String?,
  private val metadata: PyDataclassMetadata? = null,
) : PyDataclassFieldStub {

  override fun getTypeClass(): Class<PyDataclassFieldStubType> =
    PyDataclassFieldStubType::class.java

  companion object {
    fun create(expression: PyTargetExpression): PyDataclassFieldStub? {
      return PyDataclassParametersProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.buildDataclassFieldStub(expression) }
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassFieldStub? {
      val type = stream.readNameString().orEmpty()
      val calleeName = stream.readNameString() ?: return null
      val hasDefault = stream.readBoolean()
      val hasDefaultFactory = stream.readBoolean()
      val initValue = stream.readBoolean()
      val kwOnly = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val alias = stream.readNameString()
      val metadata = PyDataclassMetadata.readFrom(stream)

      return PyDataclassFieldStubImpl(
        type = type,
        calleeName = QualifiedName.fromDottedString(calleeName),
        hasDefault = hasDefault,
        hasDefaultFactory = hasDefaultFactory,
        initValue = initValue,
        kwOnly = kwOnly,
        alias = alias,
        metadata = metadata,
      )
    }
  }

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(type)
    stream.writeName(calleeName.toString())
    stream.writeBoolean(hasDefault)
    stream.writeBoolean(hasDefaultFactory)
    stream.writeBoolean(initValue)
    DataInputOutputUtil.writeNullable(stream, kwOnly, stream::writeBoolean)
    stream.writeName(alias)
    PyDataclassMetadata.writeTo(stream, metadata)
  }

  override fun getType(): String = type
  override fun getCalleeName(): QualifiedName = calleeName
  override fun hasDefault(): Boolean = hasDefault
  override fun hasDefaultFactory(): Boolean = hasDefaultFactory
  override fun initValue(): Boolean = initValue
  override fun kwOnly(): Boolean? = kwOnly
  override fun getAlias(): String? = alias
  override fun getMetadata(): PyDataclassMetadata? = metadata

  override fun toString(): String =
    "${javaClass.simpleName}(" +
    "type='$type', " +
    "calleeName=$calleeName, " +
    "hasDefault=$hasDefault, " +
    "hasDefaultFactory=$hasDefaultFactory, " +
    "initValue=$initValue, " +
    "kwOnly=$kwOnly, " +
    "alias=$alias, " +
    "metadata=$metadata" +
    ")"
}