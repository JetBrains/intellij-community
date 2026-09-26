// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.intellij.util.io.DataInputOutputUtil
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.parseDataclassParametersForStub
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyDataclassStub
import java.io.IOException


/**
 * Implementation of [PyDataclassStub] shared by every dataclass framework.
 *
 * The dataclass parameters common to every framework (decorator name + the nine boolean flags) are (de)serialized
 * through [serializeCommon]/[readCommon]. Besides those it persists the framework [type] and an opaque [metadata]
 * payload so a framework can round-trip its own configuration (e.g. Pydantic's `populate_by_name` /
 * `validate_by_name` / `validate_by_alias`) without core knowing the framework. Instances are built by [of] and
 * (de)serialized by the single [PyDataclassStubType].
 */
class PyDataclassStubImpl(
  override val type: String,
  private val decoratorName: QualifiedName?,
  private val init: Boolean?,
  private val repr: Boolean?,
  private val eq: Boolean?,
  private val order: Boolean?,
  private val unsafeHash: Boolean?,
  private val frozen: Boolean?,
  private val matchArgs: Boolean?,
  private val kwOnly: Boolean?,
  private val slots: Boolean?,
  override val metadata: PyDataclassMetadata? = null,
) : PyDataclassStub {

  override fun getTypeClass(): Class<PyDataclassStubType> = PyDataclassStubType::class.java

  companion object {
    fun create(cls: PyClass): PyDataclassStub? {
      return parseDataclassParametersForStub(cls)
    }

    /**
     * Builds the dataclass stub for the given parsed dataclass parameters. The [type] discriminator records the
     * framework; any framework-specific configuration rides along in the opaque [metadata] payload.
     */
    fun of(
      type: PyDataclassParameters.Type,
      decoratorName: QualifiedName?,
      init: Boolean?,
      repr: Boolean?,
      eq: Boolean?,
      order: Boolean?,
      unsafeHash: Boolean?,
      frozen: Boolean?,
      matchArgs: Boolean?,
      kwOnly: Boolean?,
      slots: Boolean?,
      metadata: PyDataclassMetadata? = null,
    ): PyDataclassStub =
      PyDataclassStubImpl(type.name, decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots, metadata)

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassStub =
      readCommon(stream) { decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots ->
        val type = stream.readNameString().orEmpty()
        val metadata = PyDataclassMetadata.readFrom(stream)
        PyDataclassStubImpl(
          type, decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots, metadata,
        )
      }

    /**
     * The dataclass parameters shared by every framework: decorator name + the nine boolean flags.
     *
     * The caller passes a factory to construct the desired stub-specific object from the shared payload.
     */
    @Throws(IOException::class)
    inline fun <S> readCommon(
      stream: StubInputStream,
      factory: (
        decoratorName: QualifiedName?,
        init: Boolean?,
        repr: Boolean?,
        eq: Boolean?,
        order: Boolean?,
        unsafeHash: Boolean?,
        frozen: Boolean?,
        matchArgs: Boolean?,
        kwOnly: Boolean?,
        slots: Boolean?,
      ) -> S,
    ): S {
      val decoratorName = QualifiedName.deserialize(stream)
      val init = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val repr = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val eq = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val order = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val unsafeHash = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val frozen = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val matchArgs = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val kwOnly = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val slots = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      return factory(decoratorName, init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots)
    }
  }

  override fun serialize(stream: StubOutputStream) {
    serializeCommon(stream)
    stream.writeName(type)
    PyDataclassMetadata.writeTo(stream, metadata)
  }

  /** Writes decorator name + the nine optional boolean flags. */
  private fun serializeCommon(stream: StubOutputStream) {
    QualifiedName.serialize(decoratorName, stream)
    DataInputOutputUtil.writeNullable(stream, init, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, repr, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, eq, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, order, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, unsafeHash, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, frozen, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, matchArgs, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, kwOnly, stream::writeBoolean)
    DataInputOutputUtil.writeNullable(stream, slots, stream::writeBoolean)
  }

  override fun decoratorName(): QualifiedName? = decoratorName
  override fun initValue(): Boolean? = init
  override fun reprValue(): Boolean? = repr
  override fun eqValue(): Boolean? = eq
  override fun orderValue(): Boolean? = order
  override fun unsafeHashValue(): Boolean? = unsafeHash
  override fun frozenValue(): Boolean? = frozen
  override fun matchArgsValue(): Boolean? = matchArgs
  override fun kwOnly(): Boolean? = kwOnly
  override fun slotsValue(): Boolean? = slots

  override fun toString(): String {
    return "${javaClass.simpleName}(" +
           "type='$type', " +
           "decoratorName=$decoratorName, " +
           "init=$init, " +
           "repr=$repr, " +
           "eq=$eq, " +
           "order=$order, " +
           "unsafeHash=$unsafeHash, " +
           "frozen=$frozen, " +
           "matchArgs=$matchArgs, " +
           "kwOnly=$kwOnly, " +
           "slots=$slots, " +
           "metadata=$metadata" +
           ")"
  }
}
