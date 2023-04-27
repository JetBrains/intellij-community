// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintFile
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.isBitwiseOrUnionAvailable
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.sdk.PythonSdkUtil

class PyTypeHintsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    private val genericQName = QualifiedName.fromDottedString(PyTypingTypeProvider.GENERIC)

    override fun visitPyCallExpression(node: PyCallExpression) {
      super.visitPyCallExpression(node)

      val callee = node.callee as? PyReferenceExpression
      val calleeQName = callee?.let { PyResolveUtil.resolveImportedElementQNameLocally(it) } ?: emptyList()

      if (QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR) in calleeQName) {
        val target = getTargetFromAssignment(node)

        checkTypeVarPlacement(node, target)
        checkTypeVarArguments(node, target)
        checkTypeVarRedefinition(target)
      }

      if (QualifiedName.fromDottedString(PyTypingTypeProvider.TYPING_PARAM_SPEC) in calleeQName) {
        val target = getTargetFromAssignment(node)
        checkParamSpecArguments(node, target)
      }

      checkInstanceAndClassChecks(node)

      checkParenthesesOnGenerics(node)
    }

    private fun getTargetFromAssignment(node: PyCallExpression): PyExpression? {
      val assignmentStatement = node.parent as? PyAssignmentStatement
      if (assignmentStatement == null) return null
      return assignmentStatement.targetsToValuesMapping.firstOrNull { it.second == node }?.first
    }

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      val superClassExpressions = node.superClassExpressions.asList()

      checkPlainGenericInheritance(superClassExpressions)
      checkGenericDuplication(superClassExpressions)
      checkGenericCompleteness(node)
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      super.visitPySubscriptionExpression(node)

      checkParameters(node)
      checkParameterizedBuiltins(node)
    }

    private fun checkParameterizedBuiltins(node: PySubscriptionExpression) {
      if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON39)) return

      val qualifier = node.qualifier
      if (qualifier is PyReferenceExpression) {
        val hasImportFromFuture = (node.containingFile as? PyFile)?.hasImportFromFuture(FutureFeature.ANNOTATIONS) ?: false
          if (PyBuiltinCache.isInBuiltins(qualifier) && qualifier.name in PyTypingTypeProvider.TYPING_BUILTINS_GENERIC_ALIASES &&
            !hasImportFromFuture) {
          registerProblem(node, PyPsiBundle.message("INSP.type.hints.builtin.cannot.be.parameterized.directly", qualifier.name),
                          ReplaceWithTypingGenericAliasQuickFix())
        }
      }
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      super.visitPyReferenceExpression(node)

      if (!PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) {
        return
      }

      if (node.referencedName == PyNames.CANONICAL_SELF) {
        val typeName = myTypeEvalContext.getType(node)?.name
        if (typeName != null && typeName != PyNames.CANONICAL_SELF) {
          registerProblem(node, PyPsiBundle.message("INSP.type.hints.invalid.type.self"), ProblemHighlightType.GENERIC_ERROR, null,
                          ReplaceWithTypeNameQuickFix(typeName))
        }
      }


      val isTopLevelTypeHint = node.parent is PyAnnotation ||
                               node.parent is PyExpressionStatement && node.parent.parent is PyTypeHintFile
      if (isTopLevelTypeHint) {
        if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.LITERAL, PyTypingTypeProvider.LITERAL_EXT)) {
          registerProblem(node, PyPsiBundle.message("INSP.type.hints.literal.must.have.at.least.one.parameter"))
        }
        if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.ANNOTATED, PyTypingTypeProvider.ANNOTATED_EXT)) {
          registerProblem(node, PyPsiBundle.message("INSP.type.hints.annotated.must.be.called.with.at.least.two.arguments"))
        }
      }
      else if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.TYPE_ALIAS, PyTypingTypeProvider.TYPE_ALIAS_EXT)) {
        registerProblem(node, PyPsiBundle.message("INSP.type.hints.type.alias.must.be.used.as.standalone.type.hint"))
      }
    }

    override fun visitPyFile(node: PyFile) {
      super.visitPyFile(node)

      if (node is PyTypeHintFile && PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) {
        node.children.singleOrNull().also { if (it is PyExpressionStatement) checkTupleMatching(it.expression) }
      }
    }

    override fun visitPyElement(node: PyElement) {
      super.visitPyElement(node)

      if (node is PyTypeCommentOwner &&
          node is PyAnnotationOwner &&
          node.typeComment?.text.let { it != null && !PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(it).matches() }) {
        val message = PyPsiBundle.message("INSP.type.hints.type.specified.both.in.type.comment.and.annotation")

        if (node is PyFunction) {
          if (node.annotationValue != null || node.parameterList.parameters.any { it is PyNamedParameter && it.annotationValue != null }) {
            registerProblem(node.typeComment, message, RemoveElementQuickFix(PyPsiBundle.message("QFIX.remove.type.comment")))
            registerProblem(node.nameIdentifier, message, RemoveFunctionAnnotations())
          }
        }
        else if (node.annotationValue != null) {
          registerProblem(node.typeComment, message, RemoveElementQuickFix(PyPsiBundle.message("QFIX.remove.type.comment")))
          registerProblem(node.annotation, message, RemoveElementQuickFix(PyPsiBundle.message("QFIX.remove.annotation")))
        }
      }
    }

    override fun visitPyAnnotation(node: PyAnnotation) {
      fun PyAnnotation.findSelvesInAnnotation(context: TypeEvalContext): List<PyReferenceExpression> =
        PsiTreeUtil.findChildrenOfAnyType(this.value, false, PyReferenceExpression::class.java).filter { refExpr ->
          PyTypingTypeProvider.resolveToQualifiedNames(refExpr, context).any {
            PyTypingTypeProvider.SELF == it || PyTypingTypeProvider.SELF_EXT == it
          }
        }

      val selves = node.findSelvesInAnnotation(myTypeEvalContext)
      if (selves.isEmpty()) {
        return
      }

      fun registerProblemForSelves(message: @InspectionMessage String) {
        selves.forEach {
          registerProblem(it, message)
        }
      }

      val classParent = PsiTreeUtil.getParentOfType(node, PyClass::class.java)
      if (classParent == null) {
        registerProblemForSelves(PyPsiBundle.message("INSP.type.hints.self.use.outside.class"))
      }

      val functionParent = PsiTreeUtil.getParentOfType(node, PyFunction::class.java)
      if (functionParent != null) {
        if (PyFunction.Modifier.STATICMETHOD == functionParent.modifier) {
          registerProblemForSelves(PyPsiBundle.message("INSP.type.hints.self.use.in.staticmethod"))
        }

        val parameters = functionParent.parameterList.parameters
        if (parameters.isNotEmpty()) {
          val firstParameter = parameters[0]
          val annotation = (firstParameter as? PyNamedParameter)?.annotation
          if (annotation != null && firstParameter.isSelf && annotation.findSelvesInAnnotation(myTypeEvalContext).isEmpty()) {
            val message = if (PyFunction.Modifier.CLASSMETHOD == functionParent.modifier)
              PyPsiBundle.message("INSP.type.hints.self.use.for.cls.parameter.with.self.annotation")
            else
              PyPsiBundle.message("INSP.type.hints.self.use.for.self.parameter.with.self.annotation")
            registerProblemForSelves(message)
          }
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
      checkTypeAliasTarget(node)
    }

    private fun checkTypeAliasTarget(target: PyTargetExpression) {
      val parent = target.parent
      if (parent is PyTypeDeclarationStatement) {
        val annotation = target.annotation?.value
        if (annotation is PyReferenceExpression && resolvesToAnyOfQualifiedNames(annotation, PyTypingTypeProvider.TYPE_ALIAS,
                                                                                 PyTypingTypeProvider.TYPE_ALIAS_EXT)) {
          registerProblem(target, PyPsiBundle.message("INSP.type.hints.type.alias.must.be.immediately.initialized"))
        }
      }
      else if (parent is PyAssignmentStatement &&
               PyTypingTypeProvider.isExplicitTypeAlias(parent, myTypeEvalContext) && !PyUtil.isTopLevel(parent)) {
        registerProblem(target, PyPsiBundle.message("INSP.type.hints.type.alias.must.be.top.level.declaration"))
      }
    }

    private fun checkTypeVarPlacement(call: PyCallExpression, target: PyExpression?) {
      if (target == null) {
        registerProblem(call, PyPsiBundle.message("INSP.type.hints.typevar.expression.must.be.always.directly.assigned.to.variable"))
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
          registerProblem(target, PyPsiBundle.message("INSP.type.hints.type.variables.must.not.be.redefined"))
          ControlFlowUtil.Operation.BREAK
        }
        else {
          ControlFlowUtil.Operation.NEXT
        }
      }
    }

    private fun checkParamSpecArguments(call: PyCallExpression, target: PyExpression?) {
      processMatchedArgument(call) { name, argument ->
        if (name == "name") {
          checkNameIsTheSameAsTarget(argument, target,
                                     PyPsiBundle.message("INSP.type.hints.paramspec.expects.string.literal.as.first.argument"),
                                     PyPsiBundle.message("INSP.type.hints.argument.to.paramspec.must.be.string.equal.to.variable.name"))
        }
      }
    }

    private fun checkTypeVarArguments(call: PyCallExpression, target: PyExpression?) {
      var covariant = false
      var contravariant = false
      var bound: PyExpression? = null
      val constraints = mutableListOf<PyExpression?>()

      processMatchedArgument(call) { name, argument ->
        when (name) {
          "name" ->
            checkNameIsTheSameAsTarget(argument, target,
                                       PyPsiBundle.message("INSP.type.hints.typevar.expects.string.literal.as.first.argument"),
                                       PyPsiBundle.message("INSP.type.hints.argument.to.typevar.must.be.string.equal.to.variable.name"))
          "covariant" -> covariant = PyEvaluator.evaluateAsBoolean(argument, false)
          "contravariant" -> contravariant = PyEvaluator.evaluateAsBoolean(argument, false)
          "bound" -> bound = argument
          "constraints" -> constraints.add(argument)
        }
      }

      if (covariant && contravariant) {
        registerProblem(call, PyPsiBundle.message("INSP.type.hints.bivariant.type.variables.are.not.supported"),
                        ProblemHighlightType.GENERIC_ERROR)
      }

      if (constraints.isNotEmpty() && bound != null) {
        registerProblem(call, PyPsiBundle.message("INSP.type.hints.typevar.constraints.cannot.be.combined.with.bound"),
                        ProblemHighlightType.GENERIC_ERROR)
      }

      if (constraints.size == 1) {
        registerProblem(call, PyPsiBundle.message("INSP.type.hints.single.typevar.constraint.not.allowed"),
                        ProblemHighlightType.GENERIC_ERROR)
      }

      constraints.asSequence().plus(bound).forEach {
        if (it != null) {
          val type = PyTypingTypeProvider.getType(it, myTypeEvalContext)?.get()

          if (PyTypeChecker.hasGenerics(type, myTypeEvalContext)) {
            registerProblem(it, PyPsiBundle.message("INSP.type.hints.typevar.constraints.cannot.be.parametrized.by.type.variables"))
          }
        }
      }
    }

    private fun checkNameIsTheSameAsTarget(argument: PyExpression?, target: PyExpression?,
                                           @InspectionMessage notStringLiteralMessage: String,
                                           @InspectionMessage notEqualMessage: String) {
      if (argument !is PyStringLiteralExpression) {
        registerProblem(argument, notStringLiteralMessage)
      }
      else {
        val targetName = target?.name
        if (targetName != null && targetName != argument.stringValue) {
          registerProblem(argument,
                          notEqualMessage,
                          ReplaceWithTargetNameQuickFix(targetName))
        }
      }
    }

    private fun processMatchedArgument(call: PyCallExpression,
                                       processor: (name: String?, argument: PyExpression?) -> Unit) {
      val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)
      call
        .multiMapArguments(resolveContext)
        .firstOrNull { it.unmappedArguments.isEmpty() && it.unmappedParameters.isEmpty() }
        ?.let { mapping ->
          mapping.mappedParameters.entries.forEach {
            val name = it.value.name
            val argument = PyUtil.peelArgument(it.key)
            processor(name, argument)
          }
        }
    }

    private fun checkInstanceAndClassChecks(call: PyCallExpression) {
      if (call.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
        val base = call.arguments.getOrNull(1) ?: return
        checkInstanceAndClassChecksOn(base)
      }
    }

    private fun checkInstanceAndClassChecksOn(base: PyExpression) {
      if (base is PyBinaryExpression && base.operator == PyTokenTypes.OR) {
        if (isBitwiseOrUnionAvailable(base)) {
          val left = base.leftExpression
          val right = base.rightExpression
          if (left != null) checkInstanceAndClassChecksOn(left)
          if (right != null) checkInstanceAndClassChecksOn(right)
        }
        return
      }

      checkInstanceAndClassChecksOnTypeVar(base)
      checkInstanceAndClassChecksOnReference(base)
      checkInstanceAndClassChecksOnSubscription(base)
    }

    private fun checkInstanceAndClassChecksOnTypeVar(base: PyExpression) {
      val type = myTypeEvalContext.getType(base)
      if (type is PyGenericType && !type.isDefinition ||
          type is PyCollectionType && type.elementTypes.any { it is PyGenericType } && !type.isDefinition) {
        registerProblem(base,
                        PyPsiBundle.message("INSP.type.hints.type.variables.cannot.be.used.with.instance.class.checks"),
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
              PyTypingTypeProvider.LITERAL_EXT,
              PyTypingTypeProvider.ANNOTATED,
              PyTypingTypeProvider.ANNOTATED_EXT,
              PyTypingTypeProvider.TYPE_ALIAS,
              PyTypingTypeProvider.TYPE_ALIAS_EXT,
              PyTypingTypeProvider.SELF,
              PyTypingTypeProvider.SELF_EXT -> {
                val shortName = it.substringAfterLast('.')
                registerProblem(base, PyPsiBundle.message("INSP.type.hints.type.cannot.be.used.with.instance.class.checks", shortName),
                                ProblemHighlightType.GENERIC_ERROR)
              }
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
              when (val qName = it.qualifiedName) {
                PyTypingTypeProvider.GENERIC,
                PyTypingTypeProvider.CLASS_VAR,
                PyTypingTypeProvider.FINAL,
                PyTypingTypeProvider.FINAL_EXT,
                PyTypingTypeProvider.LITERAL,
                PyTypingTypeProvider.LITERAL_EXT,
                PyTypingTypeProvider.ANNOTATED,
                PyTypingTypeProvider.ANNOTATED_EXT -> {
                  registerParametrizedGenericsProblem(qName, base)
                  return@forEach
                }

                PyTypingTypeProvider.UNION,
                PyTypingTypeProvider.OPTIONAL -> {
                  if (!isBitwiseOrUnionAvailable(base)) {
                    registerParametrizedGenericsProblem(qName, base)
                  }
                  else if (base is PySubscriptionExpression) {
                    val indexExpr = base.indexExpression
                    if (indexExpr is PyTupleExpression) {
                      indexExpr.elements.forEach { tupleElement -> checkInstanceAndClassChecksOn(tupleElement) }
                    }
                    else if (indexExpr != null) {
                      checkInstanceAndClassChecksOn(indexExpr)
                    }
                  }
                }

                PyTypingTypeProvider.CALLABLE,
                PyTypingTypeProvider.TYPE,
                PyTypingTypeProvider.PROTOCOL,
                PyTypingTypeProvider.PROTOCOL_EXT -> {
                  registerProblem(base,
                                  PyPsiBundle.message("INSP.type.hints.parameterized.generics.cannot.be.used.with.instance.class.checks"),
                                  ProblemHighlightType.GENERIC_ERROR,
                                  null,
                                  *(if (base is PySubscriptionExpression) arrayOf(RemoveGenericParametersQuickFix()) else LocalQuickFix.EMPTY_ARRAY))
                  return@forEach
                }
              }
            }

            if (it is PyTypedElement) {
              val type = myTypeEvalContext.getType(it)

              if (type is PyWithAncestors && PyTypingTypeProvider.isGeneric(type, myTypeEvalContext)) {
                registerProblem(base,
                                PyPsiBundle.message("INSP.type.hints.parameterized.generics.cannot.be.used.with.instance.class.checks"),
                                ProblemHighlightType.GENERIC_ERROR,
                                null,
                                *(if (base is PySubscriptionExpression) arrayOf(RemoveGenericParametersQuickFix()) else LocalQuickFix.EMPTY_ARRAY))
              }
            }
          }
      }
    }

    private fun registerParametrizedGenericsProblem(qName: String, base: PsiElement) {
      val shortName = qName.substringAfterLast('.')
      registerProblem(base, PyPsiBundle.message("INSP.type.hints.type.cannot.be.used.with.instance.class.checks", shortName),
                      ProblemHighlightType.GENERIC_ERROR)
    }

    private fun checkParenthesesOnGenerics(call: PyCallExpression) {
      val callee = call.callee
      if (callee is PyReferenceExpression) {
        if (PyResolveUtil.resolveImportedElementQNameLocally(callee).any { PyTypingTypeProvider.GENERIC_CLASSES.contains(it.toString()) }) {
          registerProblem(call,
                          PyPsiBundle.message("INSP.type.hints.generics.should.be.specified.through.square.brackets"),
                          ProblemHighlightType.GENERIC_ERROR,
                          null,
                          ReplaceWithSubscriptionQuickFix())
        }
        else if (PyTypingTypeProvider.isInsideTypeHint(call, myTypeEvalContext)) {
          multiFollowAssignmentsChain(callee)
            .asSequence()
            .map { if (it is PyFunction) it.containingClass else it }
            .any { it is PyWithAncestors && PyTypingTypeProvider.isGeneric(it, myTypeEvalContext) }
            .also {
              if (it) registerProblem(call, PyPsiBundle.message("INSP.type.hints.generics.should.be.specified.through.square.brackets"),
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
        .forEach {
          registerProblem(it, PyPsiBundle.message("INSP.type.hints.cannot.inherit.from.plain.generic"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
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
        .forEach {
          registerProblem(it, PyPsiBundle.message("INSP.type.hints.cannot.inherit.from.generic.multiple.times"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
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
                        PyPsiBundle.message("INSP.type.hints.some.type.variables.are.not.listed.in.generic",
                                            nonGenericTypeVarsNames, genericTypeVarsNames),
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
      val annotatedQName = QualifiedName.fromDottedString(PyTypingTypeProvider.ANNOTATED)
      val annotatedExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.ANNOTATED_EXT)
      val typeAliasQName = QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_ALIAS)
      val typeAliasExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_ALIAS_EXT)
      val typingSelf = QualifiedName.fromDottedString(PyTypingTypeProvider.SELF)
      val typingExtSelf = QualifiedName.fromDottedString(PyTypingTypeProvider.SELF_EXT)
      val qNames = PyResolveUtil.resolveImportedElementQNameLocally(operand)

      var typingOnly = true
      var callableExists = false

      qNames.forEach {
        when (it) {
          genericQName -> checkGenericParameters(index)
          literalQName, literalExtQName -> checkLiteralParameter(index)
          annotatedQName, annotatedExtQName -> checkAnnotatedParameter(index)
          typeAliasQName, typeAliasExtQName -> reportParameterizedTypeAlias(index)
          typingSelf, typingExtSelf -> reportParameterizedSelf(index)
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

    private fun reportParameterizedTypeAlias(index: PyExpression) {
      // There is another warning in the type hint context
      if (!PyTypingTypeProvider.isInsideTypeHint(index, myTypeEvalContext)) {
        registerProblem(index, PyPsiBundle.message("INSP.type.hints.type.alias.cannot.be.parameterized"),
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun reportParameterizedSelf(index: PyExpression) {
      registerProblem(index, PyPsiBundle.message("INSP.type.hints.typing.self.cannot.be.parameterized"),
                      ProblemHighlightType.GENERIC_ERROR)
    }

    private fun checkLiteralParameter(index: PyExpression) {
      val subParameter = if (index is PySubscriptionExpression) index.operand else null
      if (subParameter is PyReferenceExpression &&
          PyResolveUtil
            .resolveImportedElementQNameLocally(subParameter)
            .any { qName -> qName.toString().let { it == PyTypingTypeProvider.LITERAL || it == PyTypingTypeProvider.LITERAL_EXT } }) {
        // if `index` is like `typing.Literal[...]` and has invalid form,
        // outer `typing.Literal[...]` won't be highlighted
        return
      }

      if (PyLiteralType.fromLiteralParameter(index, myTypeEvalContext) == null) {
        registerProblem(index, PyPsiBundle.message("INSP.type.hints.illegal.literal.parameter"))
      }
    }

    private fun checkAnnotatedParameter(index: PyExpression) {
      if (index !is PyTupleExpression) {
        registerProblem(index, PyPsiBundle.message("INSP.type.hints.annotated.must.be.called.with.at.least.two.arguments"))
      }
    }

    private fun checkGenericParameters(index: PyExpression) {
      val parameters = (index as? PyTupleExpression)?.elements ?: arrayOf(index)
      val genericParameters = mutableSetOf<PsiElement>()

      parameters.forEach {
        if (it !is PyReferenceExpression) {
          registerProblem(it, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.must.all.be.type.variables"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
        else {
          val type = myTypeEvalContext.getType(it)

          if (type != null) {
            if (type is PyGenericType || isParamSpecOrConcatenate(it, myTypeEvalContext)) {
              if (!genericParameters.addAll(multiFollowAssignmentsChain(it))) {
                registerProblem(it, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.must.all.be.unique"),
                                ProblemHighlightType.GENERIC_ERROR)
              }
            }
            else {
              registerProblem(it, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.must.all.be.type.variables"),
                              ProblemHighlightType.GENERIC_ERROR)
            }
          }
        }
      }
    }

    private fun checkCallableParameters(index: PyExpression) {

      if (index !is PyTupleExpression) {
        registerProblem(index, PyPsiBundle.message("INSP.type.hints.illegal.callable.format"), ProblemHighlightType.GENERIC_ERROR)
        return
      }

      val parameters = index.elements
      if (parameters.size > 2) {
        val possiblyLastParameter = parameters[parameters.size - 2]

        registerProblem(index,
                        PyPsiBundle.message("INSP.type.hints.illegal.callable.format"),
                        ProblemHighlightType.GENERIC_ERROR,
                        null,
                        TextRange.create(0, possiblyLastParameter.startOffsetInParent + possiblyLastParameter.textLength),
                        SurroundElementsWithSquareBracketsQuickFix())
      }
      else if (parameters.size < 2) {
        registerProblem(index, PyPsiBundle.message("INSP.type.hints.illegal.callable.format"), ProblemHighlightType.GENERIC_ERROR)
      }
      else {
        val first = parameters.first()
        if (!isSdkAvailable(first) || isParamSpecOrConcatenate(first, myTypeEvalContext)) return

        if (first !is PyListLiteralExpression && !(first is PyNoneLiteralExpression && first.isEllipsis)) {
          registerProblem(first,
                          PyPsiBundle.message("INSP.type.hints.illegal.first.parameter"),
                          ProblemHighlightType.GENERIC_ERROR,
                          null,
                          if (first is PyParenthesizedExpression) ReplaceWithListQuickFix() else SurroundElementWithSquareBracketsQuickFix())
        }
      }
    }

    private fun isSdkAvailable(element: PsiElement): Boolean =
      PythonSdkUtil.findPythonSdk(ModuleUtilCore.findModuleForPsiElement(element)) != null

    private fun isParamSpecOrConcatenate(expression: PyExpression, context: TypeEvalContext) : Boolean =
      PyTypingTypeProvider.isConcatenate(expression, context) || PyTypingTypeProvider.isParamSpec(expression, context)

    private fun checkTypingMemberParameters(index: PyExpression, isCallable: Boolean) {
      val parameters = if (index is PyTupleExpression) index.elements else arrayOf(index)

      parameters
        .asSequence()
        .drop(if (isCallable) 1 else 0)
        .forEach {
          if (it is PyListLiteralExpression) {
            registerProblem(it,
                            PyPsiBundle.message("INSP.type.hints.parameters.to.generic.types.must.be.types"),
                            ProblemHighlightType.GENERIC_ERROR,
                            null,
                            RemoveSquareBracketsQuickFix())
          }
          else if (it is PyReferenceExpression && multiFollowAssignmentsChain(it).any { resolved -> resolved is PyListLiteralExpression }) {
            registerProblem(it, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.types.must.be.types"),
                            ProblemHighlightType.GENERIC_ERROR)
          }
        }
    }

    private fun checkTupleMatching(expression: PyExpression) {
      if (expression !is PyTupleExpression) return

      val assignment = PyPsiUtils.getRealContext(expression).parent as? PyAssignmentStatement ?: return
      val lhs = assignment.leftHandSideExpression ?: return

      if (PyTypingTypeProvider.mapTargetsToAnnotations(lhs, expression).isEmpty() &&
          (expression.elements.isNotEmpty() || assignment.rawTargets.isNotEmpty())) {
        registerProblem(expression, PyPsiBundle.message("INSP.type.hints.type.comment.cannot.be.matched.with.unpacked.variables"))
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
        registerProblem(node.typeComment, PyPsiBundle.message("INSP.type.hints.type.signature.has.too.few.arguments"))
      }
      else if (commentParametersSize > actualParametersSize) {
        registerProblem(node.typeComment, PyPsiBundle.message("INSP.type.hints.type.signature.has.too.many.arguments"))
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

          registerProblem(node.typeComment, PyPsiBundle.message("INSP.type.hints.type.self.not.supertype.its.class",
                                                                commentSelfTypeDescription, actualSelfTypeDescription))
        }
      }
    }

    private fun checkAnnotatedNonSelfAttribute(node: PyTargetExpression) {
      val qualifier = node.qualifier ?: return
      if (node.annotation == null && node.typeComment == null) return

      val scopeOwner = ScopeUtil.getScopeOwner(node)
      if (scopeOwner !is PyFunction) {
        registerProblem(node, PyPsiBundle.message("INSP.type.hints.non.self.attribute.could.not.be.type.hinted"))
        return
      }

      val self = scopeOwner.parameterList.parameters.firstOrNull()?.takeIf { it.isSelf }
      if (self == null ||
          PyUtil.multiResolveTopPriority(qualifier, resolveContext).let { it.isNotEmpty() && it.all { e -> e != self } }) {
        registerProblem(node, PyPsiBundle.message("INSP.type.hints.non.self.attribute.could.not.be.type.hinted"))
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
      return referenceExpression.multiFollowAssignmentsChain(resolveContext, follow).mapNotNull { it.element }
    }

    private fun resolvesToAnyOfQualifiedNames(referenceExpr: PyReferenceExpression, vararg names: String): Boolean {
      return multiFollowAssignmentsChain(referenceExpr)
        .filterIsInstance<PyQualifiedNameOwner>()
        .mapNotNull { it.qualifiedName }
        .any { names.contains(it) }
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

    private class RemoveElementQuickFix(@IntentionFamilyName private val description: String) : LocalQuickFix {

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

    private class ReplaceWithTypingGenericAliasQuickFix : LocalQuickFix {
      override fun getFamilyName(): String = PyPsiBundle.message("QFIX.replace.with.typing.alias")

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val subscription = descriptor.psiElement as? PySubscriptionExpression ?: return
        val refExpr = subscription.operand as? PyReferenceExpression ?: return
        val alias = PyTypingTypeProvider.TYPING_BUILTINS_GENERIC_ALIASES[refExpr.name] ?: return

        val languageLevel = LanguageLevel.forElement(subscription)
        val priority = if (languageLevel.isAtLeast(LanguageLevel.PYTHON35)) ImportPriority.THIRD_PARTY else ImportPriority.BUILTIN
        AddImportHelper.addOrUpdateFromImportStatement(subscription.containingFile, "typing", alias, null, priority, subscription)
        val newRefExpr = PyElementGenerator.getInstance(project).createExpressionFromText(languageLevel, alias)
        refExpr.replace(newRefExpr)
      }
    }
  }
}
