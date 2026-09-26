// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Attrs
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.getDataclassKind
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.resolveDataclassFieldParameters
import com.jetbrains.python.codeInsight.stdlib.PyAttrsDataclassType
import com.jetbrains.python.codeInsight.stdlib.PyStdlibDataclassType
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDelStatement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import one.util.streamex.StreamEx

/**
 * The framework-agnostic dataclass checks that apply to every dataclass regardless of framework
 */
internal open class PyCommonDataclassVisitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyDataclassVisitor(holder, context) {

  override fun visitPyTargetExpression(node: PyTargetExpression) {
    super.visitPyTargetExpression(node)

    checkMutatingFrozenAttribute(node)
  }

  override fun visitPyDelStatement(node: PyDelStatement) {
    super.visitPyDelStatement(node)

    node.targets
      .asSequence()
      .filterIsInstance<PyReferenceExpression>()
      .forEach { checkMutatingFrozenAttribute(it) }
  }

  override fun visitPyClass(node: PyClass) {
    super.visitPyClass(node)

    val dataclassParameters = parseDataclassParameters(node, myTypeEvalContext) ?: return

    processAnnotationsExistence(node, dataclassParameters)

    PyNamedTupleInspection.Helper.inspectFieldsOrder(
      cls = node,
      classFieldsFilter = {
        val parameters = parseDataclassParameters(it, myTypeEvalContext)
        parameters != null && !parameters.kwOnly
      },
      checkInheritedOrder = (dataclassParameters.type.name == PyStdlibDataclassType.name ||
                             dataclassParameters.type.isDataclassTransformBased),
      context = myTypeEvalContext,
      callback = this::registerProblem,
      fieldsFilter = {
        val dataclassParams = parseDataclassParameters(it.containingClass!!, myTypeEvalContext)!!
        val fieldParams = resolveDataclassFieldParameters(it.containingClass!!, dataclassParams, it, myTypeEvalContext)

        return@inspectFieldsOrder (fieldParams == null || fieldParams.initValue && !fieldParams.kwOnly) &&
                                  !(fieldParams == null && it.annotationValue == null) && // skip fields that are not annotated
                                  !PyTypingTypeProvider.isClassVar(it, myTypeEvalContext) // skip classvars
      },
      hasAssignedValue = {
        val dataclassParams = parseDataclassParameters(it.containingClass!!, myTypeEvalContext)!!
        val fieldParams = resolveDataclassFieldParameters(it.containingClass!!, dataclassParams, it, myTypeEvalContext)
        if (fieldParams != null) {
          fieldParams.hasDefault ||
          fieldParams.hasDefaultFactory ||
          dataclassParameters.type.name == PyAttrsDataclassType.name &&
          node.methods.any { m -> m.decoratorList?.findDecorator("${it.name}.default") != null }
        }
        else {
          it.hasAssignedValue()
        }
      }
    )
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    super.visitPyBinaryExpression(node)

    val leftOperator = node.referencedName
    if (leftOperator != null && ORDER_OPERATORS.contains(leftOperator)) {
      val leftClass = getInstancePyClass(node.leftExpression) ?: return
      val rightClass = getInstancePyClass(node.rightExpression) ?: return

      val (leftOrder, leftType) = getDataclassHierarchyOrder(leftClass, leftOperator)
      if (leftOrder == ClassOrder.MANUALLY) return

      val (rightOrder, _) = getDataclassHierarchyOrder(rightClass, PyNames.leftToRightOperatorName(leftOperator))

      if (leftClass == rightClass) {
        if (leftOrder == ClassOrder.DC_UNORDERED && rightOrder != ClassOrder.MANUALLY) {
          registerProblem(node.psiOperator,
                          PyPsiBundle.problemMessage("INSP.dataclasses.operator.not.supported.between.instances.of.class", leftOperator, CodifiedParam.ofReference(leftClass)),
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
      else {
        if (leftOrder == ClassOrder.DC_ORDERED ||
            leftOrder == ClassOrder.DC_UNORDERED ||
            rightOrder == ClassOrder.DC_ORDERED ||
            rightOrder == ClassOrder.DC_UNORDERED) {
          if (leftOrder == ClassOrder.DC_ORDERED &&
              leftType?.name == PyAttrsDataclassType.name &&
              rightClass.isSubclass(leftClass, myTypeEvalContext)) return // attrs allows to compare ancestor and its subclass

          registerProblem(node.psiOperator,
                          PyPsiBundle.problemMessage("INSP.dataclasses.operator.not.supported.between.instances.of.classes", leftOperator, CodifiedParam.ofReference(leftClass), CodifiedParam.ofReference(rightClass)),
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
    }
  }

  override fun visitPyCallExpression(node: PyCallExpression) {
    val callees = node.multiResolveCallee(resolveContext)
    val calleeQName = callees.mapNotNullTo(mutableSetOf()) { it.callable?.qualifiedName }.singleOrNull()

    if (calleeQName != null) {
      val dataclassType = when (calleeQName) {
        in Dataclasses.HELPER_FUNCTIONS -> PyStdlibDataclassType
        in Attrs.CLASS_HELPERS_FUNCTIONS, in Attrs.INSTANCE_HELPER_FUNCTIONS -> PyAttrsDataclassType
        else -> return
      }

      val callableType = callees.first()
      val mapping = PyCallExpressionHelper.mapArguments(node, callableType, myTypeEvalContext)

      val dataclassParameter = callableType.getParameters(myTypeEvalContext)?.firstOrNull()
      val dataclassArgument = mapping.mappedParameters.entries.firstOrNull { it.value == dataclassParameter }?.key

      if (dataclassType.name == PyStdlibDataclassType.name) {
        processHelperDataclassArgument(dataclassArgument, calleeQName)
      }
      else if (dataclassType.name == PyAttrsDataclassType.name) {
        processHelperAttrsArgument(dataclassArgument, calleeQName)
      }
    }
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    super.visitPyReferenceExpression(node)

    if (node.isQualified) {
      val cls = getInstancePyClass(node.qualifier) ?: return
      val resolved = node.getReference(resolveContext).multiResolve(false)

      if (
        resolved.isNotEmpty() &&
        resolved.asSequence()
          .map { it.element }
          .all { it is PyTargetExpression && getInitVarType(it) != null }
      ) {
        registerProblem(node.lastChild,
                        PyPsiBundle.problemMessage("INSP.dataclasses.object.could.have.no.attribute.because.it.declared.as.init.only", CodifiedParam.ofReference(cls), node.name),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }

  private fun checkMutatingFrozenAttribute(expression: PyQualifiedExpression) {
    val cls = getInstancePyClass(expression.qualifier) ?: return

    val allClasses = listOf(cls) + cls.getAncestorClasses(myTypeEvalContext)
    val allClassesAttributes = allClasses.mapNotNull { parseDataclassParameters(it, myTypeEvalContext) }
    if (allClassesAttributes.any { it.frozen == true }) {
      registerProblem(expression,
                      PyPsiBundle.problemMessage("INSP.dataclasses.object.attribute.read.only", CodifiedParam.ofReference(cls), expression.name),
                      ProblemHighlightType.GENERIC_ERROR)
    }
  }

  private fun getDataclassHierarchyOrder(cls: PyClass, operator: String?): Pair<ClassOrder, PyDataclassParameters.Type?> {
    var seenUnordered: Pair<ClassOrder, PyDataclassParameters.Type?>? = null

    for (current in StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext))) {
      val order = getDataclassOrder(current, operator)

      // `order=False` just does not add comparison methods
      // but it makes sense when no one in the hierarchy defines any of such methods
      if (order.first == ClassOrder.DC_UNORDERED) seenUnordered = order
      else if (order.first != ClassOrder.UNKNOWN) return order
    }

    return if (seenUnordered != null) seenUnordered else ClassOrder.UNKNOWN to null
  }

  private fun getDataclassOrder(cls: PyClass, operator: String?): Pair<ClassOrder, PyDataclassParameters.Type?> {
    val type = cls.getType(myTypeEvalContext)
    if (operator != null &&
        type != null &&
        !type.resolveMember(operator, null, AccessDirection.READ, resolveContext, false).isNullOrEmpty()) {
      return ClassOrder.MANUALLY to null
    }

    val parameters = parseDataclassParameters(cls, myTypeEvalContext) ?: return ClassOrder.UNKNOWN to null
    return if (parameters.order) ClassOrder.DC_ORDERED to parameters.type else ClassOrder.DC_UNORDERED to parameters.type
  }

  private fun processAnnotationsExistence(cls: PyClass, dataclassParameters: PyDataclassParameters) {
    if (dataclassParameters.type == PyStdlibDataclassType ||
        dataclassParameters.type.isDataclassTransformBased ||
        PyEvaluator.evaluateAsBoolean(PyUtil.peelArgument(dataclassParameters.others["auto_attribs"]), false)) {
      cls.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression
            && element.annotation == null
            && resolveDataclassFieldParameters(cls, dataclassParameters, element, myTypeEvalContext) != null) {
          registerProblem(element, PyPsiBundle.problemMessage("INSP.dataclasses.attribute.lacks.type.annotation", element.name),
                          ProblemHighlightType.GENERIC_ERROR)
        }

        true
      }
    }
  }

  private fun processHelperDataclassArgument(argument: PyExpression?, calleeQName: String) {
    if (argument == null) return

    val allowDefinition = calleeQName == Dataclasses.DATACLASSES_FIELDS

    val type = myTypeEvalContext.getType(argument)
    val allowSubclass = calleeQName != Dataclasses.DATACLASSES_ASDICT
    if (!isExpectedDataclass(type, PyStdlibDataclassType, allowDefinition, true, allowSubclass)) {
      val message = if (allowDefinition) {
        PyPsiBundle.problemMessage("INSP.dataclasses.method.should.be.called.on.dataclass.instances.or.types", calleeQName)
      }
      else {
        PyPsiBundle.problemMessage("INSP.dataclasses.method.should.be.called.on.dataclass.instances", calleeQName)
      }

      registerProblem(argument, message)
    }
  }

  private fun processHelperAttrsArgument(argument: PyExpression?, calleeQName: String) {
    if (argument == null) return

    val instance = calleeQName in Attrs.INSTANCE_HELPER_FUNCTIONS

    val type = myTypeEvalContext.getType(argument)
    if (!isExpectedDataclass(type, PyAttrsDataclassType, !instance, instance, true)) {
      val message = if (instance) {
        PyPsiBundle.problemMessage("INSP.dataclasses.method.should.be.called.on.attrs.instances", calleeQName)
      }
      else {
        PyPsiBundle.problemMessage("INSP.dataclasses.method.should.be.called.on.attrs.types", calleeQName)
      }

      registerProblem(argument, message)
    }
  }

  private fun isExpectedDataclass(
    type: PyType?,
    dataclassType: PyDataclassParameters.Type?,
    allowDefinition: Boolean,
    allowInstance: Boolean,
    allowSubclass: Boolean,
  ): Boolean {
    if (type is PyStructuralType || PyTypeChecker.isUnknown(type, myTypeEvalContext)) return true
    if (type is PyUnionType) return type.members.any {
      isExpectedDataclass(it, dataclassType, allowDefinition, allowInstance, allowSubclass)
    }

    return type is PyClassType &&
           (allowDefinition || !type.isDefinition) &&
           (allowInstance || type.isDefinition) &&
           (
             getDataclassKind(type.pyClass, myTypeEvalContext) == dataclassType ||
             allowSubclass && type.getAncestorTypes(myTypeEvalContext).any {
               isExpectedDataclass(it, dataclassType, true, false, false)
             }
           )
  }
}

internal enum class ClassOrder {
  MANUALLY, DC_ORDERED, DC_UNORDERED, UNKNOWN
}
