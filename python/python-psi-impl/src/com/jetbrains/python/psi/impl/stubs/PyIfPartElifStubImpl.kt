package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.jetbrains.python.PyStubElementTypes
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.PyIfPartElif
import com.jetbrains.python.psi.stubs.PyIfPartElifStub

class PyIfPartElifStubImpl(parent: StubElement<*>, private val versionCheck: PyVersionCheck)
  : StubBase<PyIfPartElif>(parent, PyStubElementTypes.IF_PART_ELIF), PyIfPartElifStub {
  override fun getVersionCheck(): PyVersionCheck = versionCheck
}
