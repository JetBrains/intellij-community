package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import com.jetbrains.python.PyStubElementTypes
import com.jetbrains.python.psi.PyElsePart
import com.jetbrains.python.psi.stubs.PyElsePartStub

class PyElsePartStubImpl(parent: StubElement<*>) : StubBase<PyElsePart>(parent, PyStubElementTypes.ELSE_PART), PyElsePartStub