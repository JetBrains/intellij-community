package com.jetbrains.python.psi.impl.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.takeWhileInclusive
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.PyIfPartElif
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyStubElementType
import com.jetbrains.python.psi.impl.PyIfPartElifImpl
import com.jetbrains.python.psi.stubs.PyIfPartElifStub

class PyIfPartElifElementType : PyStubElementType<PyIfPartElifStub, PyIfPartElif>("IF_PART_ELIF") {
  override fun createPsi(stub: PyIfPartElifStub): PyIfPartElif {
    return PyIfPartElifImpl(stub)
  }

  override fun createStub(psi: PyIfPartElif, parentStub: StubElement<out PsiElement>): PyIfPartElifStub {
    return PyIfPartElifStubImpl(parentStub, requireNotNull(PyVersionCheck.fromCondition (psi)))
  }

  override fun serialize(stub: PyIfPartElifStub, dataStream: StubOutputStream) {
    serializeVersionCheck(stub.versionCheck, dataStream)
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>): PyIfPartElifStub {
    return PyIfPartElifStubImpl(parentStub, deserializeVersionCheck(dataStream))
  }

  override fun createElement(node: ASTNode): PsiElement {
    return PyIfPartElifImpl(node)
  }

  override fun shouldCreateStub(node: ASTNode): Boolean {
    val ifStatement = node.treeParent?.psi as? PyIfStatement ?: return false
    if (!isFileOrClassTopLevel(ifStatement)) return false
    return (sequenceOf(ifStatement.ifPart) + ifStatement.elifParts.asSequence())
      .takeWhileInclusive { it !== node.psi }
      .all { PyVersionCheck.fromCondition(it) != null }
  }
}
