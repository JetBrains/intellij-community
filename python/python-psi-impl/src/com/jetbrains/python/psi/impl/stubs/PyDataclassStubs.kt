// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
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

class PyDataclassStubImpl private constructor(private val type: String,
                                              private val init: Boolean,
                                              private val repr: Boolean,
                                              private val eq: Boolean,
                                              private val order: Boolean,
                                              private val unsafeHash: Boolean,
                                              private val frozen: Boolean,
                                              private val kwOnly: Boolean) : PyDataclassStub {

  companion object {

    fun create(cls: PyClass): PyDataclassStub? {
      return parseDataclassParametersForStub(cls)?.let {
        PyDataclassStubImpl(it.type.toString(), it.init, it.repr, it.eq, it.order, it.unsafeHash, it.frozen, it.kwOnly)
      }
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassStub? {
      val type = stream.readNameString() ?: return null

      val init = stream.readBoolean()
      val repr = stream.readBoolean()
      val eq = stream.readBoolean()
      val order = stream.readBoolean()
      val unsafeHash = stream.readBoolean()
      val frozen = stream.readBoolean()
      val kwOnly = stream.readBoolean()

      return PyDataclassStubImpl(type, init, repr, eq, order, unsafeHash, frozen, kwOnly)
    }
  }

  override fun getTypeClass(): Class<out PyCustomClassStubType<out PyCustomClassStub>> = PyDataclassStubType::class.java

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(type)
    stream.writeBoolean(init)
    stream.writeBoolean(repr)
    stream.writeBoolean(eq)
    stream.writeBoolean(order)
    stream.writeBoolean(unsafeHash)
    stream.writeBoolean(frozen)
    stream.writeBoolean(kwOnly)
  }

  override fun getType(): String = type
  override fun initValue(): Boolean = init
  override fun reprValue(): Boolean = repr
  override fun eqValue(): Boolean = eq
  override fun orderValue(): Boolean = order
  override fun unsafeHashValue(): Boolean = unsafeHash
  override fun frozenValue(): Boolean = frozen
  override fun kwOnly(): Boolean = kwOnly
}
