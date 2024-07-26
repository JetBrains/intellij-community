package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.jetbrains.python.PyStubElementTypes
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.PyIfPartIf
import com.jetbrains.python.psi.stubs.PyIfPartIfStub

class PyIfPartIfStubImpl(parent: StubElement<*>, private val versionCheck: PyVersionCheck)
  : StubBase<PyIfPartIf>(parent, PyStubElementTypes.IF_PART_IF), PyIfPartIfStub {
  override fun getVersionCheck(): PyVersionCheck = versionCheck
}
