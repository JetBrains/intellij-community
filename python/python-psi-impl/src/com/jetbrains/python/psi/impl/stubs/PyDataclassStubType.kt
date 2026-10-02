// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.jetbrains.python.codeInsight.parseDataclassParametersForStub
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyDataclassStub
import org.jetbrains.annotations.ApiStatus

/**
 * The single [PyCustomClassStubType] for every dataclass framework (`Pythonid.customClassStubType`).
 *
 * Detection has one source of truth: [parseDataclassParametersForStub], which chains the per-framework detectors in
 * priority order and produces the [PyDataclassStub] with the matching framework [PyDataclassStub.type]. The stub is
 * (de)serialized by [PyDataclassStubImpl].
 */
@ApiStatus.Internal
class PyDataclassStubType : PyCustomClassStubType<PyDataclassStub>() {
  override fun createStub(psi: PyClass): PyDataclassStub? = parseDataclassParametersForStub(psi)

  override fun deserializeStub(stream: StubInputStream): PyDataclassStub = PyDataclassStubImpl.deserialize(stream)
}
