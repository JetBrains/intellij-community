// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.documentation.doctest.PyDocstringFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.*

class PyTypeHintsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(
    holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    private val genericQName = QualifiedName.fromDottedString(PyTypingTypeProvider.GENERIC)

    override fun visitPyCallExpression(node: PyCallExpression?) {
      super.visitPyCallExpression(node)

      if (node != null) {
        val callee = node.callee as? PyReferenceExpression
        val calleeQName = callee?.let { PyResolveUtil.resolveImportedElementQNameLocally(it) } ?: emptyList()

        if (QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR) in calleeQName) {
          val target = (node.parent as? PyAssignmentStatement)?.targetsToValuesMapping?.firstOrNull { it.second == node }?.first

          checkTypeVarPlacement(node, target)
          checkTypeVarArguments(node, target)
          checkTypeVarRedefinition(target)
        }

        checkInstanceAndClassChecks(node)

        checkParenthesesOnGenerics(node)
      }
    }

    override fun visitPyClass(node: PyClass?) {
      super.visitPyClass(node)

      if (node != null) {
        val superClassExpressions = node.superClassExpressions.asList()

        checkPlainGenericInheritance(superClassExpressions)
        checkGenericDuplication(superClassExpressions)
        checkGenericCompleteness(node)
      }
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      super.visitPySubscriptionExpression(node)

      checkParameters(node)
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      super.visitPyReferenceExpression(node)

      if (node.referencedName == PyNames.CANONICAL_SELF && PyTypingTypeProvider.isInAnnotationOrTypeComment(node)) {
        val typeName = myTypeEvalContext.getType(node)?.name
        if (typeName != null && typeName != PyNames.CANONICAL_SELF) {
          registerProblem(node, "Invalid type 'self'", ProblemHighlightType.GENERIC_ERROR, null,
                          ReplaceWithTypeNameQuickFix(
                            typeName))
        }
      }

      if ((node.parent is PyAnnotation || node.parent is PyExpressionStatement && node.parent.parent is PyDocstringFile) &&
          node.multiFollowAssignmentsChain(resolveContext, this::followNotTypingOpaque)
            .asSequence()
            .mapNotNull { it.element }
            .filterIsInstance<PyQualifiedNameOwner>()
            .mapNotNull { it.qualifiedName }
            .any { it == PyTypingTypeProvider.LITERAL || it == PyTypingTypeProvider.LITERAL_EXT }) {
        registerProblem(node, "'Literal' must have at least one parameter")
      }
    }

    override fun visitPyFile(node: PyFile?) {
      super.visitPyFile(node)

      if (node is PyDocstringFile && PyTypingTypeProvider.isInAnnotationOrTypeComment(node)) {
        node.children.singleOrNull().also { if (it is PyExpressionStatement) checkTupleMatching(it.expression) }
      }
    }

    override fun visitPyElement(node: PyElement?) {
      super.visitPyElement(node)

      if (node is PyTypeCommentOwner &&
          node is PyAnnotationOwner &&
          node.typeCommentAnnotation.let { it != null && it != PyTypingTypeProvider.IGNORE }) {
        val message = "Type(s) specified both in type comment and annotation"

        if (node is PyFunction) {
          if (node.annotationValue != null || node.parameterList.parameters.any { it is PyNamedParameter && it.annotationValue != null }) {
            registerProblem(node.typeComment, message,
                            RemoveElementQuickFix(
                              "Remove type comment"))
            registerProblem(node.nameIdentifier, message,
                            RemoveFunctionAnnotations())
          }
        }
        else if (node.annotationValue != null) {
          registerProblem(node.typeComment, message,
                          RemoveElementQuickFix(
                            "Remove type comment"))
          registerProblem(node.annotation, message,
                          RemoveElementQuickFix(
                            "Remove annotation"))
        }
      }
    }

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      checkTypeCommentAndParameters(node)
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)

      checkAnnotatedNonSelfAttribute(node)
    }

    private fun checkTypeVarPlacement(call: PyCallExpression, target: PyExpression?) {
      if (target == null) {
        registerProblem(call, "A 'TypeVar()' expression must always directly be assigned to a variable")
      }
    }

    private fun checkTypeVarRedefinition(target: PyExpression?) {
      val scopeOwner = ScopeUtil.getScopeOwner(target) ?: return
      val name = target?.name ?: return

      val instructions = ControlFlowCache.getControlFlow(scopeOwner).instructions
      val startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, target)

      ControlFlowUtil.iteratePrev(startInstruction, instructions) { instruction ->
        if (instruction is ReadWriteInstruction &&
            instruction.num() != startInstruction &&
            name == instruction.name &&
            instruction.access.isWriteAccess) {
          registerProblem(target, "Type variables must not be redefined")
          ControlFlowUtil.Operation.BREAK
        }
        else {
          ControlFlowUtil.Operation.NEXT
        }
      }
    }

    private fun checkTypeVarArguments(call: PyCallExpression, target: PyExpression?) {
      val resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext)
      var covariant = false
      var contravariant = false
      var bound: PyExpression? = null
      val constraints = mutableListOf<PyExpression?>()

      call
        .multiMapArguments(resolveContext)
        .firstOrNull { it.unmappedArguments.isEmpty() && it.unmappedParameters.isEmpty() }
        ?.let { mapping ->
          mapping.mappedParameters.entries.forEach {
            val name = it.value.name
            val argument = PyUtil.peelArgument(it.key)

            when (name) {
              "name" ->
                if (argument !is PyStringLiteralExpression) {
                  registerProblem(argument, "'TypeVar()' expects a string literal as first argument")
                }
                else {
                  val targetName = target?.name
                  if (targetName != null && targetName != argument.stringValue) {
                    registerProblem(argument,
                                    "The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned",
                                    ReplaceWithTargetNameQuickFix(
                                      targetName))
                  }
                }
              "covariant" -> covariant = PyEvaluator.evaluateAsBoolean(argument, false)
              "contravariant" -> contravariant = PyEvaluator.evaluateAsBoolean(argument, false)
              "bound" -> bound = argument
              "constraints" -> constraints.add(argument)
            }
          }
        }

      if (covariant && contravariant) {
        registerProblem(call, "Bivariant type variables are not supported", ProblemHighlightType.GENERIC_ERROR)
      }

      if (constraints.isNotEmpty() && bound != null) {
        registerProblem(call, "Constraints cannot be combined with bound=...", ProblemHighlightType.GENERIC_ERROR)
      }

      if (constraints.size == 1) {
        registerProblem(call, "A single constraint is not allowed", ProblemHighlightType.GENERIC_ERROR)
      }

      constraints.asSequence().plus(bound).forEach {
        if (it != null) {
          val type = PyTypingTypeProvider.getType(it, myTypeEvalContext)?.get()

          if (PyTypeChecker.hasGenerics(type, myTypeEvalContext)) {
            registerProblem(it, "Constraints cannot be parametrized by type variables")
          }
        }
      }
    }

    private fun checkInstanceAndClassChecks(call: PyCallExpression) {
      if (call.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
        val base = call.arguments.getOrNull(1) ?: return

        checkInstanceAndClassChecksOnTypeVar(base)
        checkInstanceAndClassChecksOnReference(base)
        checkInstanceAndClassChecksOnSubscription(base)
      }
    }

    private fun checkInstanceAndClassChecksOnTypeVar(base: PyExpression) {
      val type = myTypeEvalContext.getType(base)
      if (type is PyGenericType && !type.isDefinition ||
          type is PyCollectionType && type.elementTypes.any { it is PyGenericType } && !type.isDefinition) {
        registerProblem(base,
                        "Type variables cannot be used with instance and class checks",
                        ProblemHighlightType.GENERIC_ERROR)

      }
    }

    private fun checkInstanceAndClassChecksOnReference(base: PyExpression) {
      if (base is PyReferenceExpression) {
        val resolvedBase = multiFollowAssignmentsChain(base)

        resolvedBase
          .asSequence()
          .filterIsInstance<PyQualifiedNameOwner>()
          .mapNotNull { it.qualifiedName }
          .forEach {
            when (it) {
              PyTypingTypeProvider.ANY,
              PyTypingTypeProvider.UNION,
              PyTypingTypeProvider.GENERIC,
              PyTypingTypeProvider.OPTIONAL,
              PyTypingTypeProvider.CLASS_VAR,
              PyTypingTypeProvider.NO_RETURN,
              PyTypingTypeProvider.FINAL,
              PyTypingTypeProvider.FINAL_EXT,
              PyTypingTypeProvider.LITERAL,
              PyTypingTypeProvider.LITERAL_EXT ->
                registerProblem(base,
                                "'${it.substringAfterLast('.')}' cannot be used with instance and class checks",
                                ProblemHighlightType.GENERIC_ERROR)
            }
          }

        resolvedBase
          .asSequence()
          .filterIsInstance<PySubscriptionExpression>()
          .filter { myTypeEvalContext.maySwitchToAST(it) }
          .forEach { checkInstanceAndClassChecksOnSubscriptionOperand(base, it.operand) }
      }
    }

    private fun checkInstanceAndClassChecksOnSubscription(base: PyExpression) {
      if (base is PySubscriptionExpression) {
        checkInstanceAndClassChecksOnSubscriptionOperand(base, base.operand)
      }
    }

    private fun checkInstanceAndClassChecksOnSubscriptionOperand(base: PyExpression, operand: PyExpression) {
      if (operand is PyReferenceExpression) {
        multiFollowAssignmentsChain(operand)
          .forEach {
            if (it is PyQualifiedNameOwner) {
              val qName = it.qualifiedName

              when (qName) {
                PyTypingTypeProvider.GENERIC,
                PyTypingTypeProvider.UNION,
                PyTypingTypeProvider.OPTIONAL,
                PyTypingTypeProvider.CLASS_VAR,
                PyTypingTypeProvider.FINAL,
                PyTypingTypeProvider.FINAL_EXT,
                PyTypingTypeProvider.LITERAL,
                PyTypingTypeProvider.LITERAL_EXT -> {
                  registerProblem(base,
                                  "'${qName.substringAfterLast('.')}' cannot be used with instance and class checks",
                                  ProblemHighlightType.GENERIC_ERROR)
                  return@forEach
                }

                PyTypingTypeProvider.CALLABLE,
                PyTypingTypeProvider.TYPE,
                PyTypingTypeProvider.PROTOCOL,
                PyTypingTypeProvider.PROTOCOL_EXT -> {
                  registerProblem(base,
                                  "Parameterized generics cannot be used with instance and class checks",
                                  ProblemHighlightType.GENERIC_ERROR,
                                  null,
                                  if (base is PySubscriptionExpression) RemoveGenericParametersQuickFix() else null)
                  return@forEach
                }
              }
            }

            if (it is PyTypedElement) {
              val type = myTypeEvalContext.getType(it)

              if (type is PyWithAncestors && PyTypingTypeProvider.isGeneric(type, myTypeEvalContext)) {
                registerProblem(base,
                                "Parameterized generics cannot be used with instance and class checks",
                                ProblemHighlightType.GENERIC_ERROR,
                                null,
                                if (base is PySubscriptionExpression) RemoveGenericParametersQuickFix() else null)
              }
            }
          }
      }
    }

    private fun checkParenthesesOnGenerics(call: PyCallExpression) {
      val callee = call.callee
      if (callee is PyReferenceExpression) {
        if (PyResolveUtil.resolveImportedElementQNameLocally(callee).any { PyTypingTypeProvider.GENERIC_CLASSES.contains(it.toString()) }) {
          registerProblem(call,
                          "Generics should be specified through square brackets",
                          ProblemHighlightType.GENERIC_ERROR,
                          null,
                          ReplaceWithSubscriptionQuickFix())
        }
        else if (PyTypingTypeProvider.isInAnnotationOrTypeComment(call)) {
          multiFollowAssignmentsChain(callee)
            .asSequence()
            .map { if (it is PyFunction) it.containingClass else it }
            .any { it is PyWithAncestors && PyTypingTypeProvider.isGeneric(it, myTypeEvalContext) }
            .also {
              if (it) registerProblem(call, "Generics should be specified through square brackets",
                                      ReplaceWithSubscriptionQuickFix())
            }
        }
      }
    }

    private fun checkPlainGenericInheritance(superClassExpressions: List<PyExpression>) {
      superClassExpressions
        .asSequence()
        .filterIsInstance<PyReferenceExpression>()
        .filter { genericQName in PyResolveUtil.resolveImportedElementQNameLocally(it) }
        .forEach { registerProblem(it, "Cannot inherit from plain 'Generic'", ProblemHighlightType.GENERIC_ERROR) }
    }

    private fun checkGenericDuplication(superClassExpressions: List<PyExpression>) {
      superClassExpressions
        .asSequence()
        .filter { superClass ->
          val resolved = if (superClass is PyReferenceExpression) multiFollowAssignmentsChain(superClass) else listOf(superClass)

          resolved
            .asSequence()
            .filterIsInstance<PySubscriptionExpression>()
            .filter { myTypeEvalContext.maySwitchToAST(it) }
            .mapNotNull { it.operand as? PyReferenceExpression }
            .any { genericQName in PyResolveUtil.resolveImportedElementQNameLocally(it) }
        }
        .drop(1)
        .forEach { registerProblem(it, "Cannot inherit from 'Generic[...]' multiple times", ProblemHighlightType.GENERIC_ERROR) }
    }

    private fun checkGenericCompleteness(cls: PyClass) {
      var seenGeneric = false
      val genericTypeVars = linkedSetOf<PsiElement>()
      val nonGenericTypeVars = linkedSetOf<PsiElement>()

      cls.superClassExpressions.forEach { superClass ->
        val generics = collectGenerics(superClass)

        generics.first?.let {
          genericTypeVars.addAll(it)
          seenGeneric = true
        }

        nonGenericTypeVars.addAll(generics.second)
      }

      if (seenGeneric && (nonGenericTypeVars - genericTypeVars).isNotEmpty()) {
        val nonGenericTypeVarsNames = nonGenericTypeVars
          .asSequence()
          .filterIsInstance<PyTargetExpression>()
          .mapNotNull { it.name }
          .joinToString(", ")

        val genericTypeVarsNames = genericTypeVars
          .asSequence()
          .filterIsInstance<PyTargetExpression>()
          .mapNotNull { it.name }
          .joinToString(", ")

        registerProblem(cls.superClassExpressionList,
                        "Some type variables ($nonGenericTypeVarsNames) are not listed in 'Generic[$genericTypeVarsNames]'",
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun collectGenerics(superClassExpression: PyExpression): Pair<Set<PsiElement>?, Set<PsiElement>> {
      val resolvedSuperClass =
        if (superClassExpression is PyReferenceExpression) multiFollowAssignmentsChain(superClassExpression)
        else listOf(superClassExpression)

      var seenGeneric = false
      val genericTypeVars = linkedSetOf<PsiElement>()
      val nonGenericTypeVars = linkedSetOf<PsiElement>()

      resolvedSuperClass
        .asSequence()
        .filterIsInstance<PySubscriptionExpression>()
        .filter { myTypeEvalContext.maySwitchToAST(it) }
        .forEach { superSubscription ->
          val operand = superSubscription.operand
          val generic =
            operand is PyReferenceExpression &&
            genericQName in PyResolveUtil.resolveImportedElementQNameLocally(operand)

          val index = superSubscription.indexExpression
          val parameters = (index as? PyTupleExpression)?.elements ?: arrayOf(index)
          val superClassTypeVars = parameters
            .asSequence()
            .filterIsInstance<PyReferenceExpression>()
            .flatMap { multiFollowAssignmentsChain(it, this::followNotTypeVar).asSequence() }
            .filterIsInstance<PyTargetExpression>()
            .filter { myTypeEvalContext.getType(it) is PyGenericType }
            .toSet()

          if (generic) genericTypeVars.addAll(superClassTypeVars) else nonGenericTypeVars.addAll(superClassTypeVars)
          seenGeneric = seenGeneric || generic
        }

      return Pair(if (seenGeneric) genericTypeVars else null, nonGenericTypeVars)
    }

    private fun checkParameters(node: PySubscriptionExpression) {
      val operand = node.operand as? PyReferenceExpression ?: return
      val index = node.indexExpression ?: return

      val callableQName = QualifiedName.fromDottedString(PyTypingTypeProvider.CALLABLE)
      val literalQName = QualifiedName.fromDottedString(PyTypingTypeProvider.LITERAL)
      val literalExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.LITERAL_EXT)
      val qNames = PyResolveUtil.resolveImportedElementQNameLocally(operand)

      var typingOnly = true
      var callableExists = false

      qNames.forEach {
        when (it) {
          genericQName -> checkGenericParameters(index)
          literalQName, literalExtQName -> checkLiteralParameter(index)
          callableQName -> {
            callableExists = true
            checkCallableParameters(index)
          }
        }

        typingOnly = typingOnly && it.firstComponent == PyTypingTypeProvider.TYPING
      }

      if (qNames.isNotEmpty() && typingOnly) {
        checkTypingMemberParameters(index, callableExists)
      }
    }

    private fun checkLiteralParameter(index: PyExpression) {
      val subParameter = if (index is PySubscriptionExpression) index.operand else null
      if (subParameter is PyReferenceExpression &&
          PyResolveUtil
            .resolveImportedElementQNameLocally(subParameter)
            .any { qName -> qName.toString().let { it == PyTypingTypeProvider.LITERAL || it == PyTypingTypeProvider.LITERAL_EXT} }) {
        // if `index` is like `typing.Literal[...]` and has invalid form,
        // outer `typing.Literal[...]` won't be highlighted
        return
      }

      if (PyLiteralType.fromLiteralParameter(index, myTypeEvalContext) == null) {
        registerProblem(index,
                        "'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, " +
                        "other literal types, or type aliases to other literal types")
      }
    }

    private fun checkGenericParameters(index: PyExpression) {
      val parameters = (index as? PyTupleExpression)?.elements ?: arrayOf(index)
      val typeVars = mutableSetOf<PsiElement>()

      parameters.forEach {
        if (it !is PyReferenceExpression) {
          registerProblem(it, "Parameters to 'Generic[...]' must all be type variables", ProblemHighlightType.GENERIC_ERROR)
        }
        else {
          val type = myTypeEvalContext.getType(it)

          if (type != null) {
            if (type is PyGenericType) {
              if (!typeVars.addAll(multiFollowAssignmentsChain(it))) {
                registerProblem(it, "Parameters to 'Generic[...]' must all be unique", ProblemHighlightType.GENERIC_ERROR)
              }
            }
            else {
              registerProblem(it, "Parameters to 'Generic[...]' must all be type variables", ProblemHighlightType.GENERIC_ERROR)
            }
          }
        }
      }
    }

    private fun checkCallableParameters(index: PyExpression) {
      val message = "'Callable' must be used as 'Callable[[arg, ...], result]'"

      if (index !is PyTupleExpression) {
        registerProblem(index, message, ProblemHighlightType.GENERIC_ERROR)
        return
      }

      val parameters = index.elements
      if (parameters.size > 2) {
        val possiblyLastParameter = parameters[parameters.size - 2]

        registerProblem(index,
                        message,
                        ProblemHighlightType.GENERIC_ERROR,
                        null,
                        TextRange.create(0, possiblyLastParameter.startOffsetInParent + possiblyLastParameter.textLength),
                        SurroundElementsWithSquareBracketsQuickFix())
      }
      else if (parameters.size < 2) {
        registerProblem(index, message, ProblemHighlightType.GENERIC_ERROR)
      }
      else {
        val first = parameters.first()

        if (first !is PyListLiteralExpression && !(first is PyNoneLiteralExpression && first.isEllipsis)) {
          registerProblem(first,
                          message,
                          ProblemHighlightType.GENERIC_ERROR,
                          null,
                          if (first is PyParenthesizedExpression) ReplaceWithListQuickFix() else SurroundElementWithSquareBracketsQuickFix())
        }
      }
    }

    private fun checkTypingMemberParameters(index: PyExpression, isCallable: Boolean) {
      val parameters = if (index is PyTupleExpression) index.elements else arrayOf(index)

      parameters
        .asSequence()
        .drop(if (isCallable) 1 else 0)
        .forEach {
          if (it is PyListLiteralExpression) {
            registerProblem(it,
                            "Parameters to generic types must be types",
                            ProblemHighlightType.GENERIC_ERROR,
                            null,
                            RemoveSquareBracketsQuickFix())
          }
          else if (it is PyReferenceExpression && multiFollowAssignmentsChain(it).any { resolved -> resolved is PyListLiteralExpression }) {
            registerProblem(it, "Parameters to generic types must be types", ProblemHighlightType.GENERIC_ERROR)
          }
        }
    }

    private fun checkTupleMatching(expression: PyExpression) {
      if (expression !is PyTupleExpression) return

      val assignment = PyPsiUtils.getRealContext(expression).parent as? PyAssignmentStatement ?: return
      val lhs = assignment.leftHandSideExpression ?: return

      if (PyTypingTypeProvider.mapTargetsToAnnotations(lhs, expression).isEmpty() &&
          (expression.elements.isNotEmpty() || assignment.rawTargets.isNotEmpty())) {
        registerProblem(expression, "Type comment cannot be matched with unpacked variables")
      }
    }

    private fun checkTypeCommentAndParameters(node: PyFunction) {
      val functionTypeAnnotation = PyTypingTypeProvider.getFunctionTypeAnnotation(node) ?: return

      val parameterTypes = functionTypeAnnotation.parameterTypeList.parameterTypes
      if (parameterTypes.singleOrNull().let { it is PyNoneLiteralExpression && it.isEllipsis }) return

      val actualParametersSize = node.parameterList.parameters.size
      val commentParametersSize = parameterTypes.size

      val cls = node.containingClass
      val modifier = node.modifier

      val hasSelf = cls != null && modifier != PyFunction.Modifier.STATICMETHOD

      if (commentParametersSize < actualParametersSize - if (hasSelf) 1 else 0) {
        registerProblem(node.typeComment, "Type signature has too few arguments")
      }
      else if (commentParametersSize > actualParametersSize) {
        registerProblem(node.typeComment, "Type signature has too many arguments")
      }
      else if (hasSelf && actualParametersSize == commentParametersSize) {
        val actualSelfType =
          (myTypeEvalContext.getType(cls!!) as? PyInstantiableType<*>)
            ?.let { if (modifier == PyFunction.Modifier.CLASSMETHOD) it.toClass() else it.toInstance() }
          ?: return

        val commentSelfType =
          parameterTypes.firstOrNull()
            ?.let { PyTypingTypeProvider.getType(it, myTypeEvalContext) }
            ?.get()
          ?: return

        if (!PyTypeChecker.match(commentSelfType, actualSelfType, myTypeEvalContext)) {
          val actualSelfTypeDescription = PythonDocumentationProvider.getTypeDescription(actualSelfType, myTypeEvalContext)
          val commentSelfTypeDescription = PythonDocumentationProvider.getTypeDescription(commentSelfType, myTypeEvalContext)

          registerProblem(node.typeComment,
                          "The type of self '$commentSelfTypeDescription' is not a supertype of its class '$actualSelfTypeDescription'")
        }
      }
    }

    private fun checkAnnotatedNonSelfAttribute(node: PyTargetExpression) {
      val qualifier = node.qualifier ?: return
      if (node.annotation == null && node.typeComment == null) return

      val scopeOwner = ScopeUtil.getScopeOwner(node)
      if (scopeOwner !is PyFunction) {
        registerProblem(node, "Non-self attribute could not be type hinted")
        return
      }

      val self = scopeOwner.parameterList.parameters.firstOrNull()?.takeIf { it.isSelf }
      if (self == null ||
          PyUtil.multiResolveTopPriority(qualifier, resolveContext).let { it.isNotEmpty() && it.all { e -> e != self }}) {
        registerProblem(node, "Non-self attribute could not be type hinted")
      }
    }

    private fun followNotTypingOpaque(target: PyTargetExpression): Boolean {
      return !PyTypingTypeProvider.OPAQUE_NAMES.contains(target.qualifiedName)
    }

    private fun followNotTypeVar(target: PyTargetExpression): Boolean {
      return !myTypeEvalContext.maySwitchToAST(target) || target.findAssignedValue() !is PyCallExpression
    }

    private fun multiFollowAssignmentsChain(referenceExpression: PyReferenceExpression,
                                            follow: (PyTargetExpression) -> Boolean = this::followNotTypingOpaque): List<PsiElement> {
      val resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext)
      return referenceExpression.multiFollowAssignmentsChain(resolveContext, follow).mapNotNull { it.element }
    }
  }

  companion object {
    private class ReplaceWithTypeNameQuickFix(private val typeName: String) : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.type.name")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyReferenceExpression ?: return
        element.reference.handleElementRename(typeName)
      }
    }

    private class RemoveElementQuickFix(private val description: String) : LocalQuickFix {

      override fun getFamilyName() = description
      override fun applyFix(project: Project, descriptor: ProblemDescriptor) = descriptor.psiElement.delete()
    }

    private class RemoveFunctionAnnotations : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.remove.function.annotations")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = (descriptor.psiElement.parent as? PyFunction) ?: return

        function.annotation?.delete()

        function.parameterList.parameters
          .asSequence()
          .filterIsInstance<PyNamedParameter>()
          .mapNotNull { it.annotation }
          .forEach { it.delete() }
      }
    }

    private class ReplaceWithTargetNameQuickFix(private val targetName: String) : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.target.name")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val old = descriptor.psiElement as? PyStringLiteralExpression ?: return
        val new = PyElementGenerator.getInstance(project).createStringLiteral(old, targetName) ?: return

        old.replace(new)
      }
    }

    private class RemoveGenericParametersQuickFix : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.remove.generic.parameters")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val old = descriptor.psiElement as? PySubscriptionExpression ?: return

        old.replace(old.operand)
      }
    }

    private class ReplaceWithSubscriptionQuickFix : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.square.brackets")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyCallExpression ?: return

        val callee = element.callee?.text ?: return
        val argumentList = element.argumentList ?: return
        val index = argumentList.text.let { it.substring(1, it.length - 1) }

        val language = element.containingFile.language
        val text = if (language == PyFunctionTypeAnnotationDialect.INSTANCE) "() -> $callee[$index]" else "$callee[$index]"

        PsiFileFactory
          .getInstance(project)
          // it's important to create file with same language as element's file to have correct behaviour in injections
          .createFileFromText(language, text)
          ?.let { it.firstChild.lastChild as? PySubscriptionExpression }
          ?.let { element.replace(it) }
      }
    }

    private class SurroundElementsWithSquareBracketsQuickFix : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.surround.with.square.brackets")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyTupleExpression ?: return
        val list = PyElementGenerator.getInstance(project).createListLiteral()

        val originalElements = element.elements
        originalElements.dropLast(1).forEach { list.add(it) }
        originalElements.dropLast(2).forEach { it.delete() }

        element.elements.first().replace(list)
      }
    }

    private class SurroundElementWithSquareBracketsQuickFix : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.surround.with.square.brackets")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val list = PyElementGenerator.getInstance(project).createListLiteral()

        list.add(element)

        element.replace(list)
      }
    }

    private class ReplaceWithListQuickFix : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.square.brackets")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement

        val expression = (element as? PyParenthesizedExpression)?.containedExpression ?: return
        val elements = expression.let { if (it is PyTupleExpression) it.elements else arrayOf(it) }

        val list = PyElementGenerator.getInstance(project).createListLiteral()
        elements.forEach { list.add(it) }
        element.replace(list)
      }
    }

    private class RemoveSquareBracketsQuickFix : LocalQuickFix {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.remove.square.brackets")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyListLiteralExpression ?: return

        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java, true, ScopeOwner::class.java)
        val index = subscription?.indexExpression ?: return

        val newIndexElements = if (index is PyTupleExpression) {
          index.elements.flatMap { if (it == element) element.elements.asList() else listOf(it) }
        }
        else {
          element.elements.asList()
        }

        if (newIndexElements.size == 1) {
          index.replace(newIndexElements.first())
        }
        else {
          val newIndexText = newIndexElements.joinToString(prefix = "(", postfix = ")") { it.text }

          val expression = PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel.forElement(element), newIndexText)
          val newIndex = (expression as? PyParenthesizedExpression)?.containedExpression as? PyTupleExpression ?: return

          index.replace(newIndex)
        }
      }
    }
  }
}
