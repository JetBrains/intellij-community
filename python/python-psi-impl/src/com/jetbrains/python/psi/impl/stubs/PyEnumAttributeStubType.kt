package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyEnumAttributeStub
import com.jetbrains.python.psi.stubs.PyLiteralKind

class PyEnumAttributeStubType : CustomTargetExpressionStubType<PyEnumAttributeStub>() {
  override fun createStub(psi: PyTargetExpression): PyEnumAttributeStub? {
    if (ScopeUtil.getScopeOwner(psi) !is PyClass) return null

    val callExpr = PyPsiUtils.flattenParens(psi.findAssignedValue()) as? PyCallExpression ?: return null
    val callee = callExpr.callee as? PyReferenceExpression ?: return null
    val calleeFqn = PyResolveUtil.resolveImportedElementQNameLocally(callee).firstOrNull()

    if (calleeFqn == ENUM_AUTO_FQN) {
      return PyEnumAttributeStubImpl(PyLiteralKind.INT, true)
    }

    val argument = callExpr.arguments.singleOrNull() ?: return null
    val isMember = when (calleeFqn) {
      KnownDecorator.ENUM_MEMBER.qualifiedName -> true
      KnownDecorator.ENUM_NONMEMBER.qualifiedName -> false
      else -> return null
    }
    return PyEnumAttributeStubImpl(PyLiteralKind.fromExpression(argument), isMember)
  }

  override fun deserializeStub(stream: StubInputStream): PyEnumAttributeStub {
    val literalKind = PyLiteralKind.deserialize(stream)
    val isMember = stream.readBoolean()
    return PyEnumAttributeStubImpl(literalKind, isMember)
  }
}

private val ENUM_AUTO_FQN = QualifiedName.fromDottedString(PyNames.TYPE_ENUM_AUTO)

private class PyEnumAttributeStubImpl(
  override val literalKind: PyLiteralKind?,
  override val isMember: Boolean,
) : PyEnumAttributeStub {

  override fun getTypeClass(): Class<out PyCustomStubType<*, *>> = PyEnumAttributeStubType::class.java

  override fun serialize(stream: StubOutputStream) {
    PyLiteralKind.serialize(stream, literalKind)
    stream.writeBoolean(isMember)
  }

  override fun getCalleeName(): QualifiedName? = null
}