package com.jetbrains.python.psi.impl.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.jetbrains.python.psi.impl.PyVersionCheck
import com.jetbrains.python.psi.PyElsePart
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyStubElementType
import com.jetbrains.python.psi.impl.PyElsePartImpl
import com.jetbrains.python.psi.stubs.PyElsePartStub

class PyElsePartElementType : PyStubElementType<PyElsePartStub, PyElsePart>("ELSE_PART") {
  override fun createPsi(stub: PyElsePartStub): PyElsePart {
    return PyElsePartImpl(stub)
  }

  override fun createStub(psi: PyElsePart, parentStub: StubElement<out PsiElement>): PyElsePartStub {
    return PyElsePartStubImpl(parentStub)
  }

  override fun serialize(stub: PyElsePartStub, dataStream: StubOutputStream) {
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>): PyElsePartStub {
    return PyElsePartStubImpl(parentStub)
  }

  override fun createElement(node: ASTNode): PsiElement {
    return PyElsePartImpl(node)
  }

  override fun shouldCreateStub(node: ASTNode): Boolean {
    val ifStatement = node.treeParent?.psi as? PyIfStatement ?: return false
    if (!isFileOrClassTopLevel(ifStatement)) return false
    val ifParts = sequenceOf(ifStatement.ifPart) + ifStatement.elifParts.asSequence()
    return ifParts.all { PyVersionCheck.fromCondition(it) != null }
  }
}
