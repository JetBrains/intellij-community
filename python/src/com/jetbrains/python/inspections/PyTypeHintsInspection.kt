// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.*
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyGenericType
import com.jetbrains.python.psi.types.PyTypeChecker

class PyTypeHintsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

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
        val superClassExpressions = node.superClassExpressions.toList()

        checkPlainGenericInheritance(superClassExpressions)
        checkGenericDuplication(superClassExpressions)
        checkGenericCompleteness(node)
      }
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression?) {
      super.visitPySubscriptionExpression(node)

      if (node != null) {
        val callee = node.operand as? PyReferenceExpression
        val calleeQName = callee?.let { PyResolveUtil.resolveImportedElementQNameLocally(it) } ?: emptyList()

        if (genericQName in calleeQName) {
          checkGenericParameters(node.indexExpression)
        }
      }
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

      ControlFlowUtil.iteratePrev(
        startInstruction,
        instructions,
        { instruction ->
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
      )
    }

    private fun checkTypeVarArguments(call: PyCallExpression, target: PyExpression?) {
      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext)
      var covariant = false
      var contravariant = false
      var bound: PyExpression? = null
      val constraints = mutableListOf<PyExpression?>()

      call.multiMapArguments(resolveContext).firstOrNull { it.unmappedArguments.isEmpty() && it.unmappedParameters.isEmpty() }?.let {
        it.mappedParameters.entries.forEach {
          val name = it.value.name
          val argument = PyUtil.peelArgument(it.key)

          when (name) {
            "name" ->
              if (argument !is PyStringLiteralExpression) {
                registerProblem(argument, "'TypeVar()' expects a string literal as first argument")
              }
              else if (target != null && argument.stringValue != target.name) {
                registerProblem(argument, "The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned")
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
      if (myTypeEvalContext.getType(base) is PyGenericType) {
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
              PyTypingTypeProvider.NO_RETURN ->
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
                PyTypingTypeProvider.GENERIC -> {
                  registerProblem(base, "'Generic' cannot be used with instance and class checks", ProblemHighlightType.GENERIC_ERROR)
                  return@forEach
                }

                PyTypingTypeProvider.UNION,
                PyTypingTypeProvider.OPTIONAL,
                PyTypingTypeProvider.CLASS_VAR -> {
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
                                  ProblemHighlightType.GENERIC_ERROR)
                  return@forEach
                }
              }
            }

            if (it is PyTypedElement) {
              val type = myTypeEvalContext.getType(it)

              if (type is PyWithAncestors && PyTypingTypeProvider.isGeneric(type, myTypeEvalContext)) {
                registerProblem(base,
                                "Parameterized generics cannot be used with instance and class checks",
                                ProblemHighlightType.GENERIC_ERROR)
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
        else if (InjectedLanguageManager.getInstance(call.project).isInjectedFragment(call.containingFile) ||
                 PsiTreeUtil.getParentOfType(call, PyAnnotation::class.java, true, ScopeOwner::class.java) != null) {
          multiFollowAssignmentsChain(callee)
            .asSequence()
            .map { if (it is PyFunction) it.containingClass else it }
            .any { it is PyWithAncestors && PyTypingTypeProvider.isGeneric(it, myTypeEvalContext) }
            .also {
              if (it) registerProblem(call, "Generics should be specified through square brackets", ReplaceWithSubscriptionQuickFix())
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
        .filter {
          val resolved = if (it is PyReferenceExpression) multiFollowAssignmentsChain(it) else listOf(it)

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

      cls.superClassExpressions.forEach {
        val generics = collectGenerics(it)

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
        .forEach {
          val operand = it.operand
          val generic =
            operand is PyReferenceExpression &&
            genericQName in PyResolveUtil.resolveImportedElementQNameLocally(operand)

          val index = it.indexExpression
          val parameters = (index as? PyTupleExpression)?.elements ?: arrayOf(index)
          val superClassTypeVars = parameters
            .asSequence()
            .filterIsInstance<PyReferenceExpression>()
            .map { multiFollowAssignmentsChain(it, this::followNotTypeVar).toSet() }
            .fold(emptySet<PsiElement>(), { acc, typeVars -> acc.union(typeVars) })

          if (generic) genericTypeVars.addAll(superClassTypeVars) else nonGenericTypeVars.addAll(superClassTypeVars)
          seenGeneric = seenGeneric || generic
        }

      return Pair(if (seenGeneric) genericTypeVars else null, nonGenericTypeVars)
    }

    private fun checkGenericParameters(index: PyExpression?) {
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

    private fun followNotTypingOpaque(target: PyTargetExpression): Boolean {
      return !PyTypingTypeProvider.OPAQUE_NAMES.contains(target.qualifiedName)
    }

    private fun followNotTypeVar(target: PyTargetExpression): Boolean {
      return !myTypeEvalContext.maySwitchToAST(target) || target.findAssignedValue() !is PyCallExpression
    }

    private fun multiFollowAssignmentsChain(referenceExpression: PyReferenceExpression,
                                            follow: (PyTargetExpression) -> Boolean = this::followNotTypingOpaque): List<PsiElement> {
      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext)
      return referenceExpression.multiFollowAssignmentsChain(resolveContext, follow).mapNotNull { it.element }
    }
  }

  companion object {
    private class ReplaceWithSubscriptionQuickFix : LocalQuickFix {

      override fun getFamilyName() = "Replace with square brackets"

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
  }
}