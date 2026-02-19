package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.asSafely
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDictCompExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyPossibleClassMember
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PySetCompExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType
import org.jetbrains.annotations.PropertyKey

private val DICT_KEY_METHODS: List<String> = listOf("get", "pop", "setdefault")
private val SET_ELEMENT_METHODS: List<String> = listOf("add", "remove", "discard")

class PyUnhashableInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder?, val context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    override fun visitPyDictLiteralExpression(node: PyDictLiteralExpression) {
      for (element in node.elements) {
        processDictKey(element.key)
      }
    }

    override fun visitPyDictCompExpression(node: PyDictCompExpression) {
      val expression = node.resultExpression as? PyKeyValueExpression ?: return
      processDictKey(expression.key)
    }

    override fun visitPySetLiteralExpression(node: PySetLiteralExpression) {
      for (element in node.elements) {
        processSetElement(element)
      }
    }

    override fun visitPySetCompExpression(node: PySetCompExpression) {
      val expression = node.resultExpression ?: return
      processSetElement(expression)
    }

    override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
      for (target in node.targets) {
        if (target is PySubscriptionExpression && isDict(target.operand)) {
          processDictKey(target.indexExpression ?: continue)
        }
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val callee = node.callee
      val callParent = callee.asSafely<PyQualifiedExpression>()?.qualifier
      val firstArgument = node.arguments.firstOrNull() ?: return

      if (node.isCalleeText(PyNames.HASH_FUNCTION)) {
        processHashArgument(firstArgument)
      }
      if (DICT_KEY_METHODS.contains(callee?.name) && isDict(callParent)) {
        processDictKey(firstArgument)
      }
      if (SET_ELEMENT_METHODS.contains(callee?.name) && isSet(callParent)) {
        processSetElement(firstArgument)
      }
    }

    private fun isDict(expression: PyExpression?) = expression?.let {
      context.getType(expression).asSafely<PyCollectionType>()?.name
    } == PyNames.DICT

    private fun isSet(expression: PyExpression?) = expression?.let {
      context.getType(expression).asSafely<PyCollectionType>()?.name
    } == PyNames.SET

    private fun processDictKey(element: PyExpression) = registerIfUnhashable(element, "INSP.dict.key.not.hashable")
    private fun processSetElement(element: PyExpression) = registerIfUnhashable(element, "INSP.set.element.not.hashable")
    private fun processHashArgument(element: PyExpression) = registerIfUnhashable(element, "INSP.argument.not.hashable")

    private fun registerIfUnhashable(element: PyExpression, @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) messageKey: String) {
      val type = context.getType(element)
      if (!isHashable(type)) {
        registerProblem(element, PyPsiBundle.message(messageKey, type?.name))
      }
    }

    private fun isHashable(type: PyType?): Boolean =
      when (type) {
        is PyTupleType -> type.elementTypes.all { isHashable(it) }
        is PyUnionType -> type.members.all { isHashable(it) }
        is PyClassType -> isHashableClass(type)
        else -> true
      }

    private fun isHashableClass(classType: PyClassType): Boolean {
      val dataclassParameters = parseDataclassParameters(classType.pyClass, context)

      val definesHash = classType.definesMethod(PyNames.HASH)
                        ?: dataclassParameters
                          ?.run { frozen == true || unsafeHash }
                          ?.takeIf { it }
      if (definesHash != null) return definesHash

      // Python classes are hashable by default unless `__eq__` is defined
      val definesEq = classType.definesMethod(PyNames.EQ) ?: dataclassParameters?.eq
      return definesEq != true
    }

    /**
     * @return True if the class or its parent set this name to something other than None, False if to None,
     * null if the method fallbacks to `object`
     */
    private fun PyClassType.definesMethod(name: String): Boolean? {
      val member = findMember(name, resolveContext).firstOrNull()?.element as? PyTypedElement ?: return null

      val containingClass = member.asSafely<PyPossibleClassMember>()?.containingClass ?: return null
      if (PyUtil.isObjectClass(containingClass)) {
        return null
      }

      return !context.getType(member).isNoneType
    }
  }
}
