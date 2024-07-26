package com.jetbrains.python.psi.impl.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyIfPartIfImpl
import com.jetbrains.python.psi.stubs.PyIfPartIfStub

class PyIfPartIfElementType : PyStubElementType<PyIfPartIfStub, PyIfPartIf>("IF_PART_IF") {
  override fun createPsi(stub: PyIfPartIfStub): PyIfPartIf {
    return PyIfPartIfImpl(stub)
  }

  override fun createStub(psi: PyIfPartIf, parentStub: StubElement<out PsiElement>): PyIfPartIfStub {
    return PyIfPartIfStubImpl(parentStub, requireNotNull(PyVersionCheck.fromCondition (psi)))
  }

  override fun serialize(stub: PyIfPartIfStub, dataStream: StubOutputStream) {
    serializeVersionCheck(stub.versionCheck, dataStream)
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>): PyIfPartIfStub {
    return PyIfPartIfStubImpl(parentStub, deserializeVersionCheck(dataStream))
  }

  override fun createElement(node: ASTNode): PsiElement {
    return PyIfPartIfImpl(node)
  }

  override fun shouldCreateStub(node: ASTNode): Boolean {
    val psi = node.psi as PyIfPartIf
    return isFileOrClassTopLevel(psi) && PyVersionCheck.fromCondition(psi) != null
  }
}
