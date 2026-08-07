package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.containers.tailOrEmpty
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.parseStdDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.ParamHelper
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyStdlibDataclassInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, context)
  }

  class Visitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyDataclassVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      val dataclassParameters = parseStdDataclassParameters(node, myTypeEvalContext) ?: return

      processDataclassParameters(node, dataclassParameters)

      val postInit = node.findMethodByName(Dataclasses.DUNDER_POST_INIT, false, myTypeEvalContext)
      val localInitVars = mutableListOf<PyType?>()

      node.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression) {
          if (!PyTypingTypeProvider.isClassVar(element, myTypeEvalContext)) {
            processDefaultFieldValue(element)
            processAsInitVar(element, postInit)?.let { localInitVars.add(it.type) }
          }

          processFieldFunctionCall(node, dataclassParameters, element)
        }

        true
      }

      if (postInit != null) {
        processPostInitDefinition(node, postInit, dataclassParameters, localInitVars)
      }
    }

    private fun processDefaultFieldValue(field: PyTargetExpression) {
      if (field.annotationValue == null) return

      val value = field.findAssignedValue()

      if (value is PyCallExpression) {
        val fieldWithDefaultFactory = value
          .multiResolveCallee(resolveContext)
          .filter { it.callable?.qualifiedName == Dataclasses.DATACLASSES_FIELD }
          .any {
            PyCallExpressionHelper.mapArguments(value, it, myTypeEvalContext).mappedParameters.values.any { p ->
              p.name == "default_factory"
            }
          }

        if (fieldWithDefaultFactory) {
          return
        }
      }

      // Here we rely on the fact that dataclasses.field is declared to return the type of its `default` argument,
      // so if `default` is a dict, or a list instance, we will highlight the call as if the same expression
      // was in the RHS directly. dataclasses.Field itself is not considered a forbidden mutable default.
      if (PyUtil.isForbiddenMutableDefault(value, myTypeEvalContext)) {
        registerProblem(value,
                        PyPsiBundle.problemMessage("INSP.dataclasses.mutable.attribute.default.not.allowed.use.default.factory", value?.text),
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun processAsInitVar(field: PyTargetExpression, postInit: PyFunction?): InitVarField? {
      val innerInitVarType = getInitVarType(field) ?: return null
      if (postInit == null) {
        registerProblem(field,
                        PyPsiBundle.problemMessage("INSP.dataclasses.attribute.useless.until.post.init.declared", field.name),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      }
      return InitVarField(innerInitVarType)
    }

    private class InitVarField(val type: PyType?)

    private fun processPostInitDefinition(
      cls: PyClass,
      postInit: PyFunction,
      dataclassParameters: PyDataclassParameters,
      localInitVars: List<PyType?>,
    ) {
      if (!dataclassParameters.init) {
        registerProblem(postInit.nameIdentifier,
                        PyPsiBundle.message("INSP.dataclasses.post.init.would.not.be.called.until.init.parameter.set.to.true"),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)

        return
      }

      if (ParamHelper.isSelfArgsKwargsCallable(postInit, myTypeEvalContext)) return

      val allInitVars = mutableListOf<PyType?>()
      for (ancestor in cls.getAncestorClasses(myTypeEvalContext).asReversed()) {
        if (parseStdDataclassParameters(ancestor, myTypeEvalContext) == null) continue

        ancestor.processClassLevelDeclarations { element, _ ->
          if (element is PyTargetExpression) {
            allInitVars.addIfNotNull(getInitVarType(element))
          }

          return@processClassLevelDeclarations true
        }
      }
      allInitVars.addAll(localInitVars)

      val parameters = postInit.getParameters(myTypeEvalContext).tailOrEmpty()

      if (parameters.size != allInitVars.size) {
        val message = if (allInitVars.size != localInitVars.size) {
          PyPsiBundle.message("INSP.dataclasses.post.init.should.take.all.init.only.variables.including.inherited.in.same.order.they.defined")
        }
        else {
          PyPsiBundle.message("INSP.dataclasses.post.init.should.take.all.init.only.variables.in.same.order.they.defined")
        }
        registerProblem(postInit.parameterList, message, ProblemHighlightType.GENERIC_ERROR)
      }
      else {
        for ((index, callableParameter) in parameters.withIndex()) {
          val parameter = callableParameter.parameter
          if (parameter !is PyNamedParameter) continue
          val annotation = PyTypingTypeProvider.getAnnotationValue(parameter, myTypeEvalContext) ?: continue
          val typeFromAnnotation = PyTypingTypeProvider.getType(annotation, myTypeEvalContext) ?: continue
          val initVarType = allInitVars[index]
          if (!PyTypeChecker.match(typeFromAnnotation.get(), initVarType, myTypeEvalContext)) {
            val initVarTypeName = PythonDocumentationProvider.getTypeName(initVarType, myTypeEvalContext)
            val parameterTypeName = PythonDocumentationProvider.getVerboseTypeName(typeFromAnnotation.get(), myTypeEvalContext)
            registerProblem(annotation,
                            PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead",
                                                       initVarTypeName, parameterTypeName))
          }
        }
      }
    }
  }
}
