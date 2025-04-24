// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentsOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.ast.PyAstTypeParameter
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
import com.jetbrains.python.inspections.quickfix.PyUnpackTypeVarTupleQuickFix
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType
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

      if (QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR) in calleeQName ||
        QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR_EXT) in calleeQName) {
        val target = getTargetFromAssignment(node)

        checkTypeVarPlacement(node, target)
        checkTypeVarArguments(node, target)
        checkTypeVarRedefinition(target)
      }

      if (QualifiedName.fromDottedString(PyTypingTypeProvider.PARAM_SPEC) in calleeQName) {
        checkParamSpecArguments(node, getTargetFromAssignment(node))
      }

      if (QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR_TUPLE) in calleeQName ||
          QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR_TUPLE_EXT) in calleeQName) {
        checkTypeVarTupleArguments(node, getTargetFromAssignment(node))
      }

      if (QualifiedName.fromDottedString(PyTypingTypeProvider.CAST) in calleeQName ||
          QualifiedName.fromDottedString(PyTypingTypeProvider.CAST_EXT) in calleeQName) {
        checkCastArguments(node.arguments)
      }

      checkInstanceAndClassChecks(node)

      if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext) &&
          !isInsideTypingAnnotatedMetadata(node)
      ) {
        checkParenthesesOnGenerics(node)
      }
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
      checkGenericClassTypeParametersNotUsedByOuterScope(node)
      checkMetaClass(node.metaClassExpression)
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      super.visitPySubscriptionExpression(node)

      checkParameters(node)
      checkParameterizedBuiltins(node)
    }

    override fun visitPyTypeParameter(typeParameter: PyTypeParameter) {
      val defaultExpression = typeParameter.defaultExpression
      if (defaultExpression == null) return
      when(typeParameter.kind) {
        PyAstTypeParameter.Kind.TypeVar -> checkTypeVarDefaultType(defaultExpression)
        PyAstTypeParameter.Kind.ParamSpec -> checkParamSpecDefaultValue(defaultExpression)
        PyAstTypeParameter.Kind.TypeVarTuple -> checkTypeVarTupleDefaultValue(defaultExpression, typeParameter)
      }
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

    private fun checkTypeVarTupleUnpacked(node: PyReferenceExpression) {
      if (PsiTreeUtil.getParentOfType(node, PyStarExpression::class.java) != null) return
      val subscriptionExpr = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression::class.java)
      if (subscriptionExpr != null && subscriptionExpr.operand is PyQualifiedExpression &&
          (subscriptionExpr.operand as PyQualifiedExpression).asQualifiedName()?.endsWith("Unpack") == true) {
        return
      }
      holder?.registerProblem(node, PyPsiBundle.message("INSP.type.hints.type.var.tuple.must.always.be.unpacked"),
                              PyUnpackTypeVarTupleQuickFix())
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      super.visitPyReferenceExpression(node)

      val insideTypeHint = PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)
      if (insideTypeHint || isGenericTypeArgument(node)) {
        val type = Ref.deref(PyTypingTypeProvider.getType(node, myTypeEvalContext))
        if (insideTypeHint && type is PyPositionalVariadicType) {
          checkTypeVarTupleUnpacked(node)
        }
        if (type is PyTypeParameterType && type.scopeOwner == null && !isInsideTypeParameterDefault(node)) {
          registerProblem(node, PyPsiBundle.message("INSP.type.hints.unbound.type.variable"))
        }
      }

      if (!insideTypeHint) {
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

    private fun isGenericTypeArgument(node: PyReferenceExpression): Boolean {
      var element: PyElement = node
      var parentElement = element.parent
      if (parentElement is PyTupleExpression) {
        element = parentElement
        parentElement = element.parent
      }
      if (parentElement is PySubscriptionExpression && parentElement.indexExpression === element) {
        val operandType = myTypeEvalContext.getType(parentElement.operand)
        if (operandType is PyClassType && PyTypingTypeProvider.isGeneric(operandType, myTypeEvalContext)) {
          return true
        }
      }
      return false
    }

    private fun isInsideTypeParameterDefault(node: PyReferenceExpression): Boolean {
      val keywordArgument = PsiTreeUtil.getParentOfType(node, PyKeywordArgument::class.java, true, PyStatement::class.java)
      if (keywordArgument != null) {
        if (keywordArgument.name == "default" && PsiTreeUtil.isAncestor(keywordArgument.valueExpression, node, false)) {
          val callExpr = keywordArgument.parent?.parent
          if (callExpr is PyCallExpression) {
            return PyTypingTypeProvider.getTypeParameterKindFromDeclaration(callExpr, myTypeEvalContext) != null
          }
        }
        return false
      }
      val typeParameter = PsiTreeUtil.getParentOfType(node, PyTypeParameter::class.java, true, PyStatement::class.java)
      if (typeParameter != null) {
        return PsiTreeUtil.isAncestor(typeParameter.defaultExpression, node, false)
      }
      return false
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
      val annotationValue = node.value ?: return
      if (!isValidTypeHint(annotationValue, myTypeEvalContext)) {
        registerProblem(annotationValue, PyPsiBundle.message("INSP.type.hints.type.hint.is.not.valid"))
      }

      checkForwardReferencesInBinaryExpression(annotationValue)

      checkRawConcatenateUsage(annotationValue)

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
        if (PyAstFunction.Modifier.STATICMETHOD == functionParent.modifier && PyNames.NEW != functionParent.name) {
          registerProblemForSelves(PyPsiBundle.message("INSP.type.hints.self.use.in.staticmethod"))
        }

        val parameters = functionParent.parameterList.parameters
        if (parameters.isNotEmpty()) {
          val firstParameter = parameters[0]
          val annotation = (firstParameter as? PyNamedParameter)?.annotation
          if (annotation != null && firstParameter.isSelf && annotation.findSelvesInAnnotation(myTypeEvalContext).isEmpty()) {
            val message = if (PyAstFunction.Modifier.CLASSMETHOD == functionParent.modifier)
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

      val returnType = myTypeEvalContext.getReturnType(node)
      if (returnType is PyNarrowedType) {
        val parameters = node.getParameters(myTypeEvalContext)
        val isInstanceOrClassMethod = node.asMethod() != null && node.modifier != PyAstFunction.Modifier.STATICMETHOD
        val parameterIndex = if (isInstanceOrClassMethod) 1 else 0
        if (parameterIndex >= parameters.size) {
          registerProblem(node.nameIdentifier, PyPsiBundle.message("INSP.type.hints.typeIs.has.zero.parameters"))
        }
        else if (returnType.typeIs) {
          val parameter = parameters[parameterIndex]
          val parameterType = parameter.getType(myTypeEvalContext)
          if (!PyTypeChecker.match(parameterType, returnType.narrowedType, myTypeEvalContext)) {
            registerProblem(node.nameIdentifier, PyPsiBundle.message("INSP.type.hints.typeIs.does.not.match",
                                                 PythonDocumentationProvider.getTypeName(returnType.narrowedType, myTypeEvalContext),
                                                 PythonDocumentationProvider.getTypeName(parameterType, myTypeEvalContext)))
          }
        }
      }


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
      else if (parent is PyAssignmentStatement && PyTypingTypeProvider.isExplicitTypeAlias(parent, myTypeEvalContext)) {
        val assignedValue = parent.assignedValue
        if (assignedValue != null) {
          if (!isValidTypeHint(assignedValue, myTypeEvalContext)) {
            registerProblem(assignedValue, PyPsiBundle.message("INSP.type.hints.type.alias.invalid.assigned.value"))
          }
        }
        if (!PyUtil.isTopLevel(parent)) {
          registerProblem(target, PyPsiBundle.message("INSP.type.hints.type.alias.must.be.top.level.declaration"))
        }
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

        if (name == "default" && argument != null) {
          checkParamSpecDefaultValue(argument)
        }
      }
    }

    private fun checkTypeVarTupleArguments(call: PyCallExpression, target: PyExpression?) {
      processMatchedArgument(call) { name, argument ->
        if (name == "name") {
          checkNameIsTheSameAsTarget(argument, target,
                                     PyPsiBundle.message("INSP.type.hints.typevar.tuple.expects.string.literal.as.first.argument"),
                                     PyPsiBundle.message("INSP.type.hints.argument.to.typevar.tuple.must.be.string.equal.to.variable.name"))
        }
        if (name == "default" && argument != null) {
          checkTypeVarTupleDefaultValue(argument, typeParameter = null)
        }
      }
    }

    private fun checkCastArguments(arguments: Array<PyExpression>) {
      if (arguments.size == 2) {
        if (PyTypingTypeProvider.getType(arguments[0], myTypeEvalContext) == null) {
          registerProblem(arguments[0], PyPsiBundle.message("INSP.type.hints.expected.a.type"))
        }
      }
    }

    private fun checkTypeVarArguments(call: PyCallExpression, target: PyExpression?) {
      var covariant = false
      var contravariant = false
      var bound: PyExpression? = null
      var default: PyExpression? = null
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
          "default" -> default = argument
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

      default?.let { checkTypeVarDefaultType(it) }

      // TODO match bounds and constraints

      constraints.asSequence().plus(bound).forEach {
        if (it != null) {
          val type = PyTypingTypeProvider.getType(it, myTypeEvalContext)?.get()

          if (PyTypeChecker.hasGenerics(type, myTypeEvalContext)) {
            registerProblem(it, PyPsiBundle.message("INSP.type.hints.typevar.constraints.cannot.be.parametrized.by.type.variables"))
          }
        }
      }

      val boundType = bound?.let { PyTypingTypeProvider.getType(it, myTypeEvalContext)?.get() }
      if (boundType is PyClassLikeType && boundType.classQName == PyTypingTypeProvider.TYPED_DICT) {
        registerProblem(bound, PyPsiBundle.message("INSP.type.hints.typed.dict.is.not.allowed.as.a.bound.for.a.type.var"))
      }
    }

    private fun checkTypeVarDefaultType(defaultExpression: PyExpression) {
      val type = Ref.deref(PyTypingTypeProvider.getType(defaultExpression, myTypeEvalContext))
      when (type) {
        is PyParamSpecType -> registerProblem(defaultExpression, PyPsiBundle.message("INSP.type.hints.cannot.be.used.in.default.type.of.type.var", "ParamSpec"))
        is PyTypeVarTupleType -> registerProblem(defaultExpression, PyPsiBundle.message("INSP.type.hints.cannot.be.used.in.default.type.of.type.var", "TypeVarTuple"))
      }

      checkIsCorrectTypeExpression(defaultExpression)
    }

    private fun checkIsCorrectTypeExpression(expression: PyExpression) {
      if (PyTypingTypeProvider.getType(expression, myTypeEvalContext) == null) {
        registerProblem(expression, PyPsiBundle.message("INSP.type.hints.default.type.must.be.type.expression"))
      }
    }

    private fun checkTypeVarTupleDefaultValue(defaultExpression: PyExpression, typeParameter: PyTypeParameter?) {
      if ((typeParameter != null && defaultExpression is PyStarExpression) || defaultExpression is PySubscriptionExpression) {
       val type = Ref.deref(PyTypingTypeProvider.getType(defaultExpression, myTypeEvalContext))
        if (type is PyPositionalVariadicType) {
          return
        }
      }
      registerProblem(defaultExpression, PyPsiBundle.message("INSP.type.hints.default.type.of.type.var.tuple.must.be.unpacked"))
    }

    private fun checkParamSpecDefaultValue(defaultExpression: PyExpression) {
      if (defaultExpression is PyNoneLiteralExpression && defaultExpression.isEllipsis) return
      if (defaultExpression is PyListLiteralExpression) {
        defaultExpression.elements.forEach {
          checkIsCorrectTypeExpression(it)
        }
        return
      }
      if (defaultExpression is PyReferenceExpression || defaultExpression is PyStringLiteralExpression) {
        val defaultType = Ref.deref(PyTypingTypeProvider.getType(defaultExpression, myTypeEvalContext))
        if (defaultType !is PyParamSpecType) {
          registerProblem(defaultExpression, PyPsiBundle.message("INSP.type.hints.default.type.of.param.spec.must.be.param.spec.or.list.of.types"))
        }
        return
      }
      registerProblem(defaultExpression, PyPsiBundle.message("INSP.type.hints.default.type.of.param.spec.must.be.param.spec.or.list.of.types"))
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
        .firstOrNull { it.isComplete }
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

      checkInstanceAndClassChecksOnExpression(base)
      checkInstanceAndClassChecksOnReference(base)
      checkInstanceAndClassChecksOnSubscription(base)
    }

    private fun checkInstanceAndClassChecksOnExpression(base: PyExpression) {
      val type = myTypeEvalContext.getType(base)
      if (
      // T = TypeVar("T"); isinstance(x, T)
        type is PyClassType && !type.isDefinition && type.classQName.orEmpty() in PyTypingTypeProvider.TYPE_PARAMETER_FACTORIES ||
        // TODO should be caught by the type checker relying on the type hints for `isinstance`
        // p: T; isinstance(x, p)
        type is PyTypeVarType && !type.isDefinition ||
        // T = TypeVar("T"); isinstance(x, list[T])
        type is PyCollectionType && type.elementTypes.any { it is PyTypeVarType } && !type.isDefinition
      ) {
        registerProblem(base,
                        PyPsiBundle.message("INSP.type.hints.type.variables.cannot.be.used.with.instance.class.checks"),
                        ProblemHighlightType.GENERIC_ERROR)

      }
      if (type is PyTypedDictType) {
        registerProblem(base,
                        PyPsiBundle.message("INSP.type.hints.typed.dict.type.cannot.be.used.in.isinstance.tests"),
                        ProblemHighlightType.GENERIC_ERROR)
      }
      if (type is PyTypingNewType) {
        registerProblem(base,
                        PyPsiBundle.message("INSP.type.hints.new.type.type.cannot.be.used.in.isinstance.tests"),
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

              if (type is PyClassType && type.isDefinition) {
                registerProblem(base,
                                PyPsiBundle.message("INSP.type.hints.parameterized.generics.cannot.be.used.with.instance.class.checks"),
                                ProblemHighlightType.GENERIC_ERROR,
                                null,
                                *(if (base is PySubscriptionExpression) arrayOf(RemoveGenericParametersQuickFix())
                                else LocalQuickFix.EMPTY_ARRAY))
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

    private fun isInsideTypingAnnotatedMetadata(expr: PyExpression): Boolean {
      for (parent in expr.parentsOfType<PySubscriptionExpression>()) {
        val operand = parent.operand
        if (
          operand is PyReferenceExpression
          && resolvesToAnyOfQualifiedNames(operand, PyTypingTypeProvider.ANNOTATED, PyTypingTypeProvider.ANNOTATED_EXT)
        ) {
          val tuple = parent.indexExpression as? PyTupleExpression
          if (tuple != null && tuple.elements.drop(1).any { it.isAncestor(expr) }) return true
          break  // only check the immediate Annotated parent
        }
      }
      return false
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
        else {
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

    private fun checkMetaClass(metaClassExpression: PyExpression?) {
      if (metaClassExpression != null) {
        val metaClassType = myTypeEvalContext.getType(metaClassExpression)
        if (metaClassType is PyCollectionType && metaClassType.elementTypes.any { it is PyTypeVarType }) {
          registerProblem(metaClassExpression, PyPsiBundle.message("INSP.type.hints.metaclass.cannot.be.generic"))
        }
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
            .filter {
              val pyClassType = myTypeEvalContext.getType(it) as? PyClassType
              if (pyClassType == null || pyClassType.isDefinition) return@filter false
              return@filter pyClassType.classQName.orEmpty() in PyTypingTypeProvider.TYPE_PARAMETER_FACTORIES
            }
            .toSet()

          if (generic) genericTypeVars.addAll(superClassTypeVars) else nonGenericTypeVars.addAll(superClassTypeVars)
          seenGeneric = seenGeneric || generic
        }

      return Pair(if (seenGeneric) genericTypeVars else null, nonGenericTypeVars)
    }

    private fun checkGenericClassTypeParametersNotUsedByOuterScope(cls: PyClass) {
      fun getTypeParameters(clazz: PyClass): Iterable<PyTypeParameterType> {
        val clazzType = PyTypeChecker.findGenericDefinitionType(clazz, myTypeEvalContext) ?: return emptyList()
        return clazzType.elementTypes.filterIsInstance<PyTypeParameterType>()
      }

      val names = getTypeParameters(cls).map { it.name }.toMutableSet()
      if (names.isEmpty()) {
        return
      }
      val namesUsedByOuterScopes = mutableListOf<String>()
      var scopeOwner: ScopeOwner? = cls
      do {
        scopeOwner = PsiTreeUtil.getParentOfType(scopeOwner, PyClass::class.java, PyFunction::class.java)
        val typeParameters = when (scopeOwner) {
          is PyClass -> getTypeParameters(scopeOwner)
          is PyFunction -> PyTypingTypeProvider.collectTypeParameters(scopeOwner, myTypeEvalContext)
          else -> break
        }
        for (typeParameter in typeParameters) {
          val name = typeParameter.name
          if (names.remove(name)) {
            namesUsedByOuterScopes.add(name)
          }
        }
      }
      while (true)

      if (namesUsedByOuterScopes.isNotEmpty()) {
        registerProblem(cls.nameIdentifier, PyPsiBundle.message("INSP.type.hints.some.type.variables.are.used.by.an.outer.scope",
                                                                namesUsedByOuterScopes.joinToString(", ")))
      }
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
          genericQName -> checkTypingGenericParameters(node)
          literalQName, literalExtQName -> checkLiteralParameter(index)
          annotatedQName, annotatedExtQName -> checkAnnotatedParameter(index)
          typeAliasQName, typeAliasExtQName -> reportParameterizedTypeAlias(index)
          typingSelf, typingExtSelf -> reportParameterizedSelf(index)
          callableQName -> {
            callableExists = true
            checkCallableParameters(index)
          }
          else -> checkGenericTypeParameterization(node)
        }

        typingOnly = typingOnly && it.firstComponent == PyTypingTypeProvider.TYPING
      }

      if (qNames.isEmpty()) {
        checkGenericTypeParameterization(node)
      }
      else {
        if (typingOnly) {
          checkTypingMemberParameters(index, callableExists)
        }
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

    private fun checkGenericTypeParameterization(node: PySubscriptionExpression) {
      val declaration = node.operand.reference
        ?.let { PyResolveUtil.resolveDeclaration(it, resolveContext) }

      when (declaration) {
        is PyTargetExpression -> checkTypeAliasParameterization(node, declaration)
        is PyClass -> checkGenericClassParameterization(node, declaration)
        else -> return
      }
    }

    private fun checkGenericClassParameterization(node: PySubscriptionExpression, declaration: PyClass) {
      val genericDefinitionType = PyTypeChecker.findGenericDefinitionType(declaration, myTypeEvalContext)
      if (genericDefinitionType == null) {
        if (PyTypingTypeProvider.isGeneric(declaration, myTypeEvalContext) &&
            declaration.findMethodByName(PyNames.CLASS_GETITEM, false, myTypeEvalContext) == null) {
          registerProblem(node.indexExpression,
                          PyPsiBundle.message("INSP.type.hints.type.arguments.class.is.already.parameterized",
                                              declaration.name))
        }
        return
      }
      val typeArguments = checkGenericTypeArguments(node)

      if (typeArguments == null || genericDefinitionType.pyClass.qualifiedName == PyNames.TUPLE) return
      val typeParameters = genericDefinitionType.elementTypes

      val typeParameterListRepresentation = typeParameters.joinToString(prefix = "[", postfix = "]") { it.name!! }

      val message =  PyPsiBundle.message("INSP.type.hints.type.arguments.do.not.match.type.parameters.of.class",
                                         typeParameterListRepresentation,
                                         genericDefinitionType.pyClass.name)

      checkTypeArgumentsMatchTypeParameters(node, typeParameters, typeArguments, message)
    }

    private fun checkTypeAliasParameterization(node: PySubscriptionExpression, declaration: PyTargetExpression) {
      val assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(declaration) ?: return
      if (PyTypingTypeProvider.resolveToQualifiedNames(assignedValue, myTypeEvalContext)
            .any { PyTypingTypeProvider.OPAQUE_NAMES.contains(it) }) return
      val assignedValueType = Ref.deref(PyTypingTypeProvider.getType(assignedValue, myTypeEvalContext)) ?: return

      val isExplicitTypeAlias = declaration.annotationValue != null
      val generics = collectTypeParametersFromTypeAlias(assignedValue, assignedValueType, isExplicitTypeAlias)
      if (generics.isEmpty) {
        registerProblem(node.indexExpression, PyPsiBundle.message("INSP.type.hints.generic.type.alias.is.not.generic.or.already.parameterized"), ProblemHighlightType.WARNING)
        return
      }
      val typeArguments = checkGenericTypeArguments(node)
      val typeParameters = generics.allTypeParameters.distinct()
      if (typeArguments != null) {
        val message = PyPsiBundle.message("INSP.type.hints.type.arguments.do.not.match.type.parameters.of.alias", declaration.name)
        checkTypeArgumentsMatchTypeParameters(node, typeParameters, typeArguments, message)
      }
    }

    private fun collectTypeParametersFromTypeAlias(assignedValue: PyExpression, assignedValueType: PyType, isExplicitTypeAlias: Boolean): PyTypeChecker.Generics {
      if (isExplicitTypeAlias || !(assignedValue is PyReferenceExpression && assignedValueType is PyClassType)) {
        return PyTypeChecker.collectGenerics(assignedValueType, myTypeEvalContext)
      }
      else {
          val genericDefinitionType = PyTypeChecker.findGenericDefinitionType(assignedValueType.pyClass, myTypeEvalContext)
                                      ?: return PyTypeChecker.Generics()
          return PyTypeChecker.collectGenerics(genericDefinitionType, myTypeEvalContext)
        }
    }


    private fun checkGenericTypeArguments(node: PySubscriptionExpression): List<PyType?>? {
      val indexExpression = node.indexExpression ?: return null
      val parameters = (indexExpression as? PyTupleExpression)?.elements ?: arrayOf(indexExpression)
      val typeArgumentTypes = mutableListOf<PyType?>()

      parameters.forEach {
        when (it) {
          is PyReferenceExpression,
          is PySubscriptionExpression,
          is PyBinaryExpression,
          is PyStarExpression,
          is PyStringLiteralExpression,
          is PyListLiteralExpression, -> {
            val typeRef = PyTypingTypeProvider.getType(it, myTypeEvalContext)
            if (typeRef == null) {
              val shouldReportError = when {
                it is PyReferenceExpression -> {
                  val isUnresolved = PyResolveUtil.resolveDeclaration(it.reference, resolveContext) == null
                  val isOpaque = PyTypingTypeProvider.resolveToQualifiedNames(it, myTypeEvalContext)
                    .any { qName -> PyTypingTypeProvider.OPAQUE_NAMES.contains(qName) }
                  !isOpaque && !isUnresolved
                }
                else -> true
              }
              if (shouldReportError) {
                registerProblem(it, PyPsiBundle.message("INSP.type.hints.invalid.type.argument"))
              }
            }
            typeArgumentTypes.add(Ref.deref(typeRef))
          }
          is PyNoneLiteralExpression -> {
            typeArgumentTypes.add(if (it.isEllipsis) null else PyNoneType.INSTANCE)
          }
          else -> {
            registerProblem(it, PyPsiBundle.message("INSP.type.hints.invalid.type.argument"))
            typeArgumentTypes.add(null)
          }
        }
      }
      return typeArgumentTypes
    }

    private fun checkTypingGenericParameters(node: PySubscriptionExpression) {
      val indexExpression = node.indexExpression ?: return
      val typeExpressions = (indexExpression as? PyTupleExpression)?.elements ?: arrayOf(indexExpression)
      val typeParams = mutableSetOf<PyTypeParameterType>()
      val typeParamDeclarations = mutableSetOf<PyQualifiedNameOwner>()
      var lastIsDefault = false
      var lastIsTypeVarTuple = false

      for (typeExpr in typeExpressions) {
        if (typeExpr !is PyReferenceExpression && typeExpr !is PyStarExpression && typeExpr !is PySubscriptionExpression) {
          registerProblem(typeExpr, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.must.all.be.type.variables"),
                          ProblemHighlightType.GENERIC_ERROR)
          continue
        }
        val typeParameterType = Ref.deref(PyTypingTypeProvider.getType(typeExpr, myTypeEvalContext))
        if (typeParameterType !is PyTypeParameterType) {
          registerProblem(typeExpr, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.must.all.be.type.variables"),
                          ProblemHighlightType.GENERIC_ERROR)
          continue
        }
        if (!typeParams.add(typeParameterType)) {
          registerProblem(typeExpr, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.must.all.be.unique"),
                          ProblemHighlightType.GENERIC_ERROR)
        }

        val defaultType = typeParameterType.defaultType
        if (defaultType != null) {
          lastIsDefault = true
          if (lastIsTypeVarTuple && typeParameterType is PyTypeVarType) {
            registerProblem(typeExpr,
                            PyPsiBundle.message("INSP.type.hints.default.type.var.cannot.follow.type.var.tuple"),
                            ProblemHighlightType.GENERIC_ERROR)
          }
          val genericTypesInDefaultExpr = PyTypeChecker.collectGenerics(Ref.deref(defaultType), myTypeEvalContext)
          val defaultOutOfScope = genericTypesInDefaultExpr.allTypeParameters
            .firstOrNull { typeVar -> typeVar.declarationElement != null && typeVar.declarationElement !in typeParamDeclarations }

          if (defaultOutOfScope != null) {
            registerProblem(typeExpr,
                            PyPsiBundle.message("INSP.type.hints.default.type.refers.to.type.var.out.of.scope", defaultOutOfScope.name))
          }
        }
        else if (lastIsDefault) {
          registerProblem(typeExpr,
                          PyPsiBundle.message("INSP.type.hints.non.default.type.vars.cannot.follow.defaults"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
        val typeParamDeclaration = typeParameterType.declarationElement
        if (typeParamDeclaration != null) {
          typeParamDeclarations.add(typeParamDeclaration)
        }
        lastIsTypeVarTuple = typeParameterType is PyTypeVarTupleType
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

    private fun isParamSpecOrConcatenate(expression: PyExpression, context: TypeEvalContext): Boolean {
      if (expression !is PyReferenceExpression && expression !is PySubscriptionExpression) return false
      val parametersType = Ref.deref(PyTypingTypeProvider.getType(expression, context))
      return parametersType is PyParamSpecType || parametersType is PyConcatenateType
    } 

    private fun checkRawConcatenateUsage(expression: PyExpression) {
      if (expression is PySubscriptionExpression &&
          Ref.deref(PyTypingTypeProvider.getType(expression, myTypeEvalContext)) is PyConcatenateType) {
          registerProblem(expression,
                          PyPsiBundle.message("INSP.type.hints.concatenate.can.only.be.used.inside.callable"),
                          ProblemHighlightType.WARNING)
        }
    }

    private fun checkForwardReferencesInBinaryExpression(expression: PyExpression) {
      if (expression is PyBinaryExpression && expression.operator == PyTokenTypes.OR) {
        expression.accept(object : PyRecursiveElementVisitor() {
          override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
            if (node.parent is PyBinaryExpression) {
              registerProblem(node, PyPsiBundle.message("INSP.type.hints.forward.reference.in.union"),
                              ProblemHighlightType.GENERIC_ERROR)
            }
            super.visitPyStringLiteralExpression(node)
          }
        })
      }
    }

    private fun checkTypingMemberParameters(index: PyExpression, isCallable: Boolean) {
      val parameters = if (index is PyTupleExpression) index.elements else arrayOf(index)

      var alreadyHaveUnpacking = false
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
          else if (it is PyStarExpression) {
            if (alreadyHaveUnpacking) {
              registerProblem(it, PyPsiBundle.message("INSP.type.hints.parameters.to.generic.types.cannot.contain.more.than.one.unpacking"),
                              ProblemHighlightType.GENERIC_ERROR)
            }
            else {
              alreadyHaveUnpacking = true
            }
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

      val hasSelf = cls != null && modifier != PyAstFunction.Modifier.STATICMETHOD

      if (commentParametersSize < actualParametersSize - if (hasSelf) 1 else 0) {
        registerProblem(node.typeComment, PyPsiBundle.message("INSP.type.hints.type.signature.has.too.few.arguments"))
      }
      else if (commentParametersSize > actualParametersSize) {
        registerProblem(node.typeComment, PyPsiBundle.message("INSP.type.hints.type.signature.has.too.many.arguments"))
      }
      else if (hasSelf && actualParametersSize == commentParametersSize) {
        val actualSelfType =
          (myTypeEvalContext.getType(cls!!) as? PyInstantiableType<*>)
            ?.let { if (modifier == PyAstFunction.Modifier.CLASSMETHOD) it.toClass() else it.toInstance() }
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

    private fun checkTypeArgumentsMatchTypeParameters(node: PySubscriptionExpression,
                                                      typeParameters: List<PyType>,
                                                      typeArguments: List<PyType?>,
                                                      @InspectionMessage message: String) {
      val mapping = PyTypeParameterMapping.mapByShape(typeParameters,
                                                      typeArguments,
                                                      PyTypeParameterMapping.Option.USE_DEFAULTS)
      if (mapping == null) {
        registerProblem(node.indexExpression, message, ProblemHighlightType.WARNING)
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
      return target.qualifiedName?.let { PyTypingTypeProvider.OPAQUE_NAMES.contains(it) } == false
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
    private class ReplaceWithTypeNameQuickFix(private val typeName: String) : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.type.name")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PyReferenceExpression) return
        element.reference.handleElementRename(typeName)
      }
    }

    private class RemoveElementQuickFix(@IntentionFamilyName private val description: String) : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = description
      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) = element.delete()
    }

    private class RemoveFunctionAnnotations : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.remove.function.annotations")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val function = (element.parent as? PyFunction) ?: return

        function.annotation?.delete()

        function.parameterList.parameters
          .asSequence()
          .filterIsInstance<PyNamedParameter>()
          .mapNotNull { it.annotation }
          .forEach { it.delete() }
      }
    }

    private class ReplaceWithTargetNameQuickFix(private val targetName: String) : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.target.name")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PyStringLiteralExpression) return
        val new = PyElementGenerator.getInstance(project).createStringLiteral(element, targetName) ?: return

        element.replace(new)
      }
    }

    private class RemoveGenericParametersQuickFix : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.remove.generic.parameters")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PySubscriptionExpression) return

        element.replace(element.operand)
      }
    }

    private class ReplaceWithSubscriptionQuickFix : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.square.brackets")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PyCallExpression) return

        val callee = element.callee?.text ?: return
        val argumentList = element.argumentList ?: return
        val index = argumentList.text.let { it.substring(1, it.length - 1) }

        val language = element.containingFile.language
        val text = if (language == PyFunctionTypeAnnotationDialect.INSTANCE) "() -> $callee[$index]" else "$callee[$index]"

        PsiFileFactory
          .getInstance(project)
          // it's important to create file with same language as element's file to have correct behaviour in injections
          .createFileFromText("x.py", language, text, false, true)
          ?.let { it.firstChild.lastChild as? PySubscriptionExpression }
          ?.let { element.replace(it) }
      }
    }

    private class SurroundElementsWithSquareBracketsQuickFix : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.surround.with.square.brackets")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PyTupleExpression) return
        val list = PyElementGenerator.getInstance(project).createListLiteral()

        val originalElements = element.elements
        originalElements.dropLast(1).forEach { list.add(it) }
        originalElements.dropLast(2).forEach { it.delete() }

        element.elements.first().replace(list)
      }
    }

    private class SurroundElementWithSquareBracketsQuickFix : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.surround.with.square.brackets")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val list = PyElementGenerator.getInstance(project).createListLiteral()

        list.add(element)

        element.replace(list)
      }
    }

    private class ReplaceWithListQuickFix : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.replace.with.square.brackets")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val expression = (element as? PyParenthesizedExpression)?.containedExpression ?: return
        val elements = expression.let { if (it is PyTupleExpression) it.elements else arrayOf(it) }

        val list = PyElementGenerator.getInstance(project).createListLiteral()
        elements.forEach { list.add(it) }
        element.replace(list)
      }
    }

    private class RemoveSquareBracketsQuickFix : PsiUpdateModCommandQuickFix() {

      override fun getFamilyName() = PyPsiBundle.message("QFIX.remove.square.brackets")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PyListLiteralExpression) return

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

          val expression = PyElementGenerator.getInstance(project)
            .createExpressionFromText(LanguageLevel.forElement(element), newIndexText)
          val newIndex = (expression as? PyParenthesizedExpression)?.containedExpression as? PyTupleExpression ?: return

          index.replace(newIndex)
        }
      }
    }

    private class ReplaceWithTypingGenericAliasQuickFix : PsiUpdateModCommandQuickFix() {
      override fun getFamilyName(): String = PyPsiBundle.message("QFIX.replace.with.typing.alias")

      override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        if (element !is PySubscriptionExpression) return
        val refExpr = element.operand as? PyReferenceExpression ?: return
        val alias = PyTypingTypeProvider.TYPING_BUILTINS_GENERIC_ALIASES[refExpr.name] ?: return

        val languageLevel = LanguageLevel.forElement(element)
        val priority = if (languageLevel.isAtLeast(LanguageLevel.PYTHON35)) ImportPriority.THIRD_PARTY else ImportPriority.BUILTIN
        AddImportHelper.addOrUpdateFromImportStatement(element.containingFile, "typing", alias, null, priority, element)
        val newRefExpr = PyElementGenerator.getInstance(project).createExpressionFromText(languageLevel, alias)
        refExpr.replace(newRefExpr)
      }
    }

    fun isValidTypeHint(expression: PyExpression, context: TypeEvalContext): Boolean {
      return when (expression) {
        is PyListLiteralExpression -> false
        is PyCallExpression -> Ref.deref(PyTypingTypeProvider.getType(expression, context)) is PyTypeVarType
        is PySubscriptionExpression -> expression.operand is PyReferenceExpression
        is PyStringLiteralExpression -> stringLiteralIsCorrectTypeHint(expression, context)
        is PyReferenceExpression -> referenceIsCorrectTypeHint(expression, context)
        else -> PyTypingTypeProvider.getType(expression, context) != null
      }
    }

    private fun stringLiteralIsCorrectTypeHint(stringLiteral: PyStringLiteralExpression, context: TypeEvalContext): Boolean {
      val embeddedString = stringLiteral.firstChild as? PyPlainStringElement ?: return false // f-strings are not allowed
      if (embeddedString.prefix.isNotEmpty()) return false // prefixed strings are not allowed
      if (stringLiteral.stringElements.size > 1) return false
      val expressionText = if (embeddedString.isTripleQuoted) {
        "(${embeddedString.content.trimIndent()})"
      }
      else {
        embeddedString.content
      }
      val embeddedExpression = PyUtil.createExpressionFromFragment(expressionText, stringLiteral.containingFile) ?: return false
      return isValidTypeHint(embeddedExpression, context)
    }

    private fun referenceIsCorrectTypeHint(referenceExpression: PyReferenceExpression, context: TypeEvalContext): Boolean {
      val resolveContext = PyResolveContext.defaultContext(context)
      val resolvedElement = PyResolveUtil.resolveDeclaration(referenceExpression.reference, resolveContext)
      if (resolvedElement == null) return true // We cannot be sure, let it better be false-negative
      return when (resolvedElement) {
        is PyTargetExpression -> {
          val qName = resolvedElement.qualifiedName ?: return true
          if (PyTypingTypeProvider.OPAQUE_NAMES.contains(qName)) return true
          val assignedTypeAliasValue = PyTypingAliasStubType.getAssignedValueStubLike(resolvedElement)
          if (assignedTypeAliasValue != null) {
            return isValidTypeHint(assignedTypeAliasValue, context)
          }
          else {
            val type = Ref.deref(PyTypingTypeProvider.getType(referenceExpression, context))
            return type is PyClassLikeType
          }
        }
        is PyTypeParameter, is PyClass, is PyTypeAliasStatement -> true
        is PyFunction -> resolvedElement.qualifiedName?.let {
          it.endsWith("ParamSpec.args") || it.endsWith("ParamSpec.kwargs")
        } == true
        else -> false
      }
    }
  }
}
