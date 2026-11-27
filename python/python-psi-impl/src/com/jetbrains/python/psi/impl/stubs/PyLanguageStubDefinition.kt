// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.LanguageStubDefinition

/** The stub support of the Python and the PythonStub languages. */
internal class PyLanguageStubDefinition : LanguageStubDefinition {
  override val stubVersion: Int
    get() = STUB_VERSION

  override val builder: StubBuilder
    get() = PyFileStubBuilder()

  companion object {
    /**
     * The version of the Python stub tree. `PyFileElementType` returns it for the file of a dialect.
     * After a change of the number, update the version of each index that uses the changed stub elements.
     */
    const val STUB_VERSION: Int = 127
  }
}
