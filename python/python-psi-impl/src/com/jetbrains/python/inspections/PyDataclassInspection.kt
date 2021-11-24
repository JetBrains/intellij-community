/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.isNullOrEmpty
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.*
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.ParamHelper
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.types.*
import one.util.streamex.StreamEx

class PyDataclassInspection : PyInspection() {

  companion object {
    private val ORDER_OPERATORS = setOf("__lt__", "__le__", "__gt__", "__ge__")
    private val DATACLASSES_HELPERS = setOf("dataclasses.fields", "dataclasses.asdict", "dataclasses.astuple", "dataclasses.replace")
    private val ATTRS_HELPERS = setOf("attr.fields",
                                      "attr.fields_dict",
                                      "attr.asdict",
                                      "attr.astuple",
                                      "attr.assoc",
                                      "attr.evolve")

    private enum class ClassOrder {
      MANUALLY, DC_ORDERED, DC_UNORDERED, UNKNOWN
    }
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(
    holder,PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

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

      val dataclassParameters = parseDataclassParameters(node, myTypeEvalContext)

      if (dataclassParameters != null) {
        if (dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD) {
          processDataclassParameters(node, dataclassParameters)

          val postInit = node.findMethodByName(DUNDER_POST_INIT, false, myTypeEvalContext)
          val localInitVars = mutableListOf<PyTargetExpression>()

          node.processClassLevelDeclarations { element, _ ->
            if (element is PyTargetExpression) {
              if (!PyTypingTypeProvider.isClassVar(element, myTypeEvalContext)) {
                processDefaultFieldValue(element)
                processAsInitVar(element, postInit)?.let { localInitVars.add(it) }
              }

              processFieldFunctionCall(element)
            }

            true
          }

          if (postInit != null) {
            processPostInitDefinition(node, postInit, dataclassParameters, localInitVars)
          }
        }
        else if (dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
          processAttrsParameters(node, dataclassParameters)

          node
            .findMethodByName(DUNDER_ATTRS_POST_INIT, false, myTypeEvalContext)
            ?.also { processAttrsPostInitDefinition(it, dataclassParameters) }

          processAttrsDefaultThroughDecorator(node)
          processAttrsInitializersAndValidators(node)
          processAttrIbFunctionCalls(node)
        }

        processAnnotationsExistence(node, dataclassParameters)

        PyNamedTupleInspection.inspectFieldsOrder(
          node,
          {
            val parameters = parseDataclassParameters(it, myTypeEvalContext)
            parameters != null && !parameters.kwOnly
          },
          dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD,
          myTypeEvalContext,
          this::registerProblem,
          {
            val stub = it.stub
            val fieldStub = if (stub == null) PyDataclassFieldStubImpl.create(it)
            else stub.getCustomStub(PyDataclassFieldStub::class.java)

            (fieldStub == null || fieldStub.initValue() && !fieldStub.kwOnly()) &&
            !(fieldStub == null && it.annotationValue == null) && // skip fields that are not annotated
            !PyTypingTypeProvider.isClassVar(it, myTypeEvalContext) // skip classvars
          },
          {
            val fieldStub = PyDataclassFieldStubImpl.create(it)

            if (fieldStub != null) {
              fieldStub.hasDefault() ||
              fieldStub.hasDefaultFactory() ||
              dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS &&
              node.methods.any { m -> m.decoratorList?.findDecorator("${it.name}.default") != null }
            }
            else {
              val assignedValue = it.findAssignedValue()
              assignedValue != null && !resolvesToOmittedDefault(assignedValue, dataclassParameters.type)
            }
          }
        )
      }
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
                            PyPsiBundle.message("INSP.dataclasses.operator.not.supported.between.instances.of.class", leftOperator, leftClass.name),
                            ProblemHighlightType.GENERIC_ERROR)
          }
        }
        else {
          if (leftOrder == ClassOrder.DC_ORDERED ||
              leftOrder == ClassOrder.DC_UNORDERED ||
              rightOrder == ClassOrder.DC_ORDERED ||
              rightOrder == ClassOrder.DC_UNORDERED) {
            if (leftOrder == ClassOrder.DC_ORDERED &&
                leftType?.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS &&
                rightClass.isSubclass(leftClass, myTypeEvalContext)) return // attrs allows to compare ancestor and its subclass

            registerProblem(node.psiOperator,
                            PyPsiBundle.message("INSP.dataclasses.operator.not.supported.between.instances.of.classes", leftOperator, leftClass.name, rightClass.name),
                            ProblemHighlightType.GENERIC_ERROR)
          }
        }
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val callees = node.multiResolveCallee(resolveContext)
      val calleeQName = callees.mapNotNullTo(mutableSetOf()) { it.callable?.qualifiedName }.singleOrNull()

      if (calleeQName != null) {
        val dataclassType = when {
          DATACLASSES_HELPERS.contains(calleeQName) -> PyDataclassParameters.PredefinedType.STD
          ATTRS_HELPERS.contains(calleeQName) -> PyDataclassParameters.PredefinedType.ATTRS
          else -> return
        }

        val callableType = callees.first()
        val mapping = PyCallExpressionHelper.mapArguments(node, callableType, myTypeEvalContext)

        val dataclassParameter = callableType.getParameters(myTypeEvalContext)?.firstOrNull()
        val dataclassArgument = mapping.mappedParameters.entries.firstOrNull { it.value == dataclassParameter }?.key

        if (dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.STD) {
          processHelperDataclassArgument(dataclassArgument, calleeQName)
        }
        else if (dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
          processHelperAttrsArgument(dataclassArgument, calleeQName)
        }
      }
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      super.visitPyReferenceExpression(node)

      if (node.isQualified) {
        val cls = getInstancePyClass(node.qualifier) ?: return
        val resolved = node.getReference(resolveContext).multiResolve(false)

        if (resolved.isNotEmpty() && resolved.asSequence().map { it.element }.all { it is PyTargetExpression && isInitVar(it) }) {
          registerProblem(node.lastChild,
                          PyPsiBundle.message("INSP.dataclasses.object.could.have.no.attribute.because.it.declared.as.init.only", cls.name, node.name),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }
    }

    private fun checkMutatingFrozenAttribute(expression: PyQualifiedExpression) {
      val cls = getInstancePyClass(expression.qualifier) ?: return

      if (StreamEx
          .of(cls).append(cls.getAncestorClasses(myTypeEvalContext))
          .mapNotNull { parseDataclassParameters(it, myTypeEvalContext) }
          .any { it.frozen }) {
        registerProblem(expression,
                        PyPsiBundle.message("INSP.dataclasses.object.attribute.read.only", cls.name, expression.name),
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

    private fun getInstancePyClass(element: PyTypedElement?): PyClass? {
      val type = element?.let { myTypeEvalContext.getType(it) } as? PyClassType
      return if (type != null && !type.isDefinition) type.pyClass else null
    }

    private fun processDataclassParameters(cls: PyClass, dataclassParameters: PyDataclassParameters) {
      if (!dataclassParameters.eq && dataclassParameters.order) {
        registerProblem(dataclassParameters.eqArgument, PyPsiBundle.message("INSP.dataclasses.eq.must.be.true.if.order.true"), ProblemHighlightType.GENERIC_ERROR)
      }

      var initMethodExists = false
      var reprMethodExists = false
      var eqMethodExists = false
      var orderMethodsExist = false
      var mutatingMethodsExist = false
      var hashMethodExists = false

      cls.methods.forEach {
        when (it.name) {
          PyNames.INIT -> initMethodExists = true
          "__repr__" -> reprMethodExists = true
          "__eq__" -> eqMethodExists = true
          in ORDER_OPERATORS -> orderMethodsExist = true
          "__setattr__", "__delattr__" -> mutatingMethodsExist = true
          PyNames.HASH -> hashMethodExists = true
        }
      }

      hashMethodExists = hashMethodExists || cls.findClassAttribute(PyNames.HASH, false, myTypeEvalContext) != null

      // argument to register problem, argument name and method name
      val useless = mutableListOf<Triple<PyExpression?, String, String>>()

      if (dataclassParameters.init && initMethodExists) {
        useless.add(Triple(dataclassParameters.initArgument, "init", PyNames.INIT))
      }

      if (dataclassParameters.repr && reprMethodExists) {
        useless.add(Triple(dataclassParameters.reprArgument, "repr", "__repr__"))
      }

      if (dataclassParameters.eq && eqMethodExists) {
        useless.add(Triple(dataclassParameters.eqArgument, "eq", "__eq__"))
      }

      useless.forEach {
        registerProblem(it.first,
                        PyPsiBundle.message("INSP.dataclasses.argument.ignored.if.class.already.defines.method", it.second, it.third),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }

      if (dataclassParameters.order && orderMethodsExist) {
        registerProblem(dataclassParameters.orderArgument,
                        PyPsiBundle.message("INSP.dataclasses.order.argument.should.be.false.if.class.defines.one.of.order.methods"),
                        ProblemHighlightType.GENERIC_ERROR)
      }

      if (dataclassParameters.frozen && mutatingMethodsExist) {
        registerProblem(dataclassParameters.frozenArgument,
                        PyPsiBundle.message("INSP.dataclasses.frozen.attribute.should.be.false.if.class.defines.setattr.or.delattr"),
                        ProblemHighlightType.GENERIC_ERROR)
      }

      if (dataclassParameters.unsafeHash && hashMethodExists) {
        registerProblem(dataclassParameters.unsafeHashArgument,
                        PyPsiBundle.message("INSP.dataclasses.unsafe.hash.attribute.should.be.false.if.class.defines.hash"),
                        ProblemHighlightType.GENERIC_ERROR)
      }

      var frozenInHierarchy: Boolean? = null
      for (current in StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext))) {
        val currentFrozen = parseStdDataclassParameters(current, myTypeEvalContext)?.frozen ?: continue

        if (frozenInHierarchy == null) {
          frozenInHierarchy = currentFrozen
        }
        else if (frozenInHierarchy != currentFrozen) {
          registerProblem(dataclassParameters.frozenArgument ?: cls.nameIdentifier,
                          PyPsiBundle.message("INSP.dataclasses.frozen.dataclasses.can.not.inherit.non.frozen.one"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
    }

    private fun processAttrsParameters(cls: PyClass, dataclassParameters: PyDataclassParameters) {
      var initMethod: PyFunction? = null
      var reprMethod: PyFunction? = null
      var strMethod: PyFunction? = null
      val cmpMethods = mutableListOf<PyFunction>()
      val mutatingMethods = mutableListOf<PyFunction>()
      var hashMethod: PsiNameIdentifierOwner? = null

      cls.methods.forEach {
        when (it.name) {
          PyNames.INIT -> initMethod = it
          "__repr__" -> reprMethod = it
          "__str__" -> strMethod = it
          "__eq__",
          in ORDER_OPERATORS -> cmpMethods.add(it)
          "__setattr__", "__delattr__" -> mutatingMethods.add(it)
          PyNames.HASH -> hashMethod = it
        }
      }

      hashMethod = hashMethod ?: cls.findClassAttribute(PyNames.HASH, false, myTypeEvalContext)

      // element to register problem and corresponding attr.s parameter
      val problems = mutableListOf<Pair<PsiNameIdentifierOwner?, String>>()

      if (dataclassParameters.init && initMethod != null) {
        problems.add(initMethod to "init")
      }

      if (dataclassParameters.repr && reprMethod != null) {
        problems.add(reprMethod to "repr")
      }

      if (PyEvaluator.evaluateAsBoolean(PyUtil.peelArgument(dataclassParameters.others["str"]), false) && strMethod != null) {
        problems.add(strMethod to "str")
      }

      if (dataclassParameters.order && cmpMethods.isNotEmpty()) {
        cmpMethods.forEach { problems.add(it to "cmp/order") }
      }

      if (dataclassParameters.frozen && mutatingMethods.isNotEmpty()) {
        mutatingMethods.forEach { problems.add(it to "frozen") }
      }

      if (dataclassParameters.unsafeHash && hashMethod != null) {
        problems.add(hashMethod to "hash")
      }

      problems.forEach {
        it.first?.apply {
          registerProblem(nameIdentifier,
                          PyPsiBundle.message("INSP.dataclasses.method.is.ignored.if.class.already.defines.parameter", name, it.second),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }

      if (dataclassParameters.order && dataclassParameters.frozen && hashMethod != null) {
        registerProblem(hashMethod?.nameIdentifier,
                        PyPsiBundle.message("INSP.dataclasses.hash.ignored.if.class.already.defines.cmp.or.order.or.frozen.parameters"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }

    private fun processDefaultFieldValue(field: PyTargetExpression) {
      if (field.annotationValue == null) return

      val value = field.findAssignedValue()

      if (value is PyCallExpression) {
        val fieldWithDefaultFactory = value
          .multiResolveCallee(resolveContext)
          .filter { it.callable?.qualifiedName == "dataclasses.field" }
          .any {
            PyCallExpressionHelper.mapArguments(value, it, myTypeEvalContext).mappedParameters.values.any { p ->
              p.name == "default_factory"
            }
          }

        if (fieldWithDefaultFactory) {
          return
        }
      }

      if (PyUtil.isForbiddenMutableDefault(value, myTypeEvalContext)) {
        registerProblem(value,
                        PyPsiBundle.message("INSP.dataclasses.mutable.attribute.default.not.allowed.use.default.factory", value?.text),
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun processAttrsDefaultThroughDecorator(cls: PyClass) {
      val initializers = mutableMapOf<String, MutableList<PyFunction>>()

      cls.methods.forEach { method ->
        val decorators = method.decoratorList?.decorators

        if (decorators != null) {
          decorators
            .asSequence()
            .mapNotNull { it.qualifiedName }
            .filter { it.componentCount == 2 && it.endsWith("default") }
            .mapNotNull { it.firstComponent }
            .firstOrNull()
            ?.also { name ->
              val attribute = cls.findClassAttribute(name, false, myTypeEvalContext)
              if (attribute != null) {
                initializers.computeIfAbsent(name, { mutableListOf() }).add(method)

                val stub = PyDataclassFieldStubImpl.create(attribute)
                if (stub != null && (stub.hasDefault() || stub.hasDefaultFactory())) {
                  registerProblem(method.nameIdentifier,
                                  PyPsiBundle.message("INSP.dataclasses.attribute.default.is.set.using.attr.ib"),
                                  ProblemHighlightType.GENERIC_ERROR)
                }
              }
            }
        }
      }

      initializers.values.forEach { sameAttrInitializers ->
        val first = sameAttrInitializers[0]

        sameAttrInitializers
          .asSequence()
          .drop(1)
          .forEach { registerProblem(it.nameIdentifier,
                                     PyPsiBundle.message("INSP.dataclasses.attribute.default.set.using.method", first.name),
                                     ProblemHighlightType.GENERIC_ERROR) }
      }
    }

    private fun processAttrsInitializersAndValidators(cls: PyClass) {
      cls.visitMethods(
        { method ->
          val decorators = method.decoratorList?.decorators

          if (decorators != null) {
            decorators
              .asSequence()
              .mapNotNull { it.qualifiedName }
              .filter { it.componentCount == 2 }
              .mapNotNull { it.lastComponent }
              .forEach {
                val expectedParameters = when (it) {
                  "default" -> 1
                  "validator" -> 3
                  else -> return@forEach
                }

                val actualParameters = method.parameterList
                if (actualParameters.parameters.size != expectedParameters) {
                  val message = PyPsiBundle.message("INSP.dataclasses.method.should.take.only.n.parameter", method.name, expectedParameters)

                  registerProblem(actualParameters, message, ProblemHighlightType.GENERIC_ERROR)
                }
              }
          }

          true
        },
        false,
        myTypeEvalContext
      )
    }

    private fun processAnnotationsExistence(cls: PyClass, dataclassParameters: PyDataclassParameters) {
      if (dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD ||
          PyEvaluator.evaluateAsBoolean(PyUtil.peelArgument(dataclassParameters.others["auto_attribs"]), false)) {
        cls.processClassLevelDeclarations { element, _ ->
          if (element is PyTargetExpression && element.annotation == null && PyDataclassFieldStubImpl.create(element) != null) {
            registerProblem(element, PyPsiBundle.message("INSP.dataclasses.attribute.lacks.type.annotation", element.name),
                            ProblemHighlightType.GENERIC_ERROR)
          }

          true
        }
      }
    }

    private fun processAttrIbFunctionCalls(cls: PyClass) {
      cls.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression) {
          val call = element.findAssignedValue() as? PyCallExpression
          val stub = PyDataclassFieldStubImpl.create(element)

          if (call != null && stub != null) {
            if (stub.hasDefaultFactory()) {
              if (stub.hasDefault()) {
                registerProblem(call.argumentList, PyPsiBundle.message("INSP.dataclasses.cannot.specify.both.default.and.factory"),
                                ProblemHighlightType.GENERIC_ERROR)
              }
              else {
                // at least covers the following case: `attr.ib(default=attr.Factory(...), factory=...)`

                val default = call.getKeywordArgument("default")
                val factory = call.getKeywordArgument("factory")

                if (default != null && factory != null && !resolvesToOmittedDefault(default, PyDataclassParameters.PredefinedType.ATTRS)) {
                  registerProblem(call.argumentList, PyPsiBundle.message("INSP.dataclasses.cannot.specify.both.default.and.factory"),
                                  ProblemHighlightType.GENERIC_ERROR)
                }
              }
            }
          }
        }

        true
      }
    }

    private fun processAsInitVar(field: PyTargetExpression, postInit: PyFunction?): PyTargetExpression? {
      if (isInitVar(field)) {
        if (postInit == null) {
          registerProblem(field,
                          PyPsiBundle.message("INSP.dataclasses.attribute.useless.until.post.init.declared", field.name),
                          ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }

        return field
      }

      return null
    }

    private fun processFieldFunctionCall(field: PyTargetExpression) {
      val fieldStub = PyDataclassFieldStubImpl.create(field) ?: return
      val call = field.findAssignedValue() as? PyCallExpression ?: return

      if (PyTypingTypeProvider.isClassVar(field, myTypeEvalContext) || isInitVar(field)) {
        if (fieldStub.hasDefaultFactory()) {
          registerProblem(call.getKeywordArgument("default_factory"),
                          PyPsiBundle.message("INSP.dataclasses.field.cannot.have.default.factory"),
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
      else if (fieldStub.hasDefault() && fieldStub.hasDefaultFactory()) {
        registerProblem(call.argumentList, PyPsiBundle.message("INSP.dataclasses.cannot.specify.both.default.and.default.factory"),
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun processPostInitDefinition(cls: PyClass,
                                          postInit: PyFunction,
                                          dataclassParameters: PyDataclassParameters,
                                          localInitVars: List<PyTargetExpression>) {
      if (!dataclassParameters.init) {
        registerProblem(postInit.nameIdentifier,
                        PyPsiBundle.message("INSP.dataclasses.post.init.would.not.be.called.until.init.parameter.set.to.true"),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)

        return
      }

      if (ParamHelper.isSelfArgsKwargsCallable(postInit, myTypeEvalContext)) return

      val allInitVars = mutableListOf<PyTargetExpression>()
      for (ancestor in cls.getAncestorClasses(myTypeEvalContext).asReversed()) {
        if (parseStdDataclassParameters(ancestor, myTypeEvalContext) == null) continue

        ancestor.processClassLevelDeclarations { element, _ ->
          if (element is PyTargetExpression && isInitVar(element)) {
            allInitVars.add(element)
          }

          return@processClassLevelDeclarations true
        }
      }
      allInitVars.addAll(localInitVars)

      val implicitParameters = postInit.getParameters(myTypeEvalContext)
      val parameters = if (implicitParameters.isEmpty()) emptyList<PyCallableParameter>() else ContainerUtil.subList(implicitParameters, 1)

      val message = if (allInitVars.size != localInitVars.size) {
        PyPsiBundle.message("INSP.dataclasses.post.init.should.take.all.init.only.variables.including.inherited.in.same.order.they.defined")
      }
      else {
        PyPsiBundle.message("INSP.dataclasses.post.init.should.take.all.init.only.variables.in.same.order.they.defined")
      }


      if (parameters.size != allInitVars.size) {
        registerProblem(postInit.parameterList, message, ProblemHighlightType.GENERIC_ERROR)
      }
      else {
        parameters
          .asSequence()
          .zip(allInitVars.asSequence())
          .all { it.first.name == it.second.name }
          .also { if (!it) registerProblem(postInit.parameterList, message) }
      }
    }

    private fun processAttrsPostInitDefinition(postInit: PyFunction, dataclassParameters: PyDataclassParameters) {
      if (!dataclassParameters.init) {
        registerProblem(postInit.nameIdentifier,
                        PyPsiBundle.message("INSP.dataclasses.attrs.post.init.would.not.be.called.until.init.parameter.set.to.true"),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      }

      if (postInit.getParameters(myTypeEvalContext).size != 1) {
        registerProblem(postInit.parameterList,
                        PyPsiBundle.message("INSP.dataclasses.attrs.post.init.should.not.take.any.parameters.except.self"),
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun processHelperDataclassArgument(argument: PyExpression?, calleeQName: String) {
      if (argument == null) return

      val allowDefinition = calleeQName == "dataclasses.fields"

      val type = myTypeEvalContext.getType(argument)
      val allowSubclass = calleeQName != "dataclasses.asdict"
      if (!isExpectedDataclass(type, PyDataclassParameters.PredefinedType.STD, allowDefinition, true, allowSubclass)) {
        val message = if (allowDefinition) {
          PyPsiBundle.message("INSP.dataclasses.method.should.be.called.on.dataclass.instances.or.types", calleeQName)
        }
        else {
          PyPsiBundle.message("INSP.dataclasses.method.should.be.called.on.dataclass.instances", calleeQName)
        }

        registerProblem(argument, message)
      }
    }

    private fun processHelperAttrsArgument(argument: PyExpression?, calleeQName: String) {
      if (argument == null) return

      val instance = calleeQName != "attr.fields" && calleeQName != "attr.fields_dict"

      val type = myTypeEvalContext.getType(argument)
      if (!isExpectedDataclass(type, PyDataclassParameters.PredefinedType.ATTRS, !instance, instance, true)) {
        val message = if (instance) {
          PyPsiBundle.message("INSP.dataclasses.method.should.be.called.on.attrs.instances", calleeQName)
        }
        else {
          PyPsiBundle.message("INSP.dataclasses.method.should.be.called.on.attrs.types", calleeQName)
        }

        registerProblem(argument, message)
      }
    }

    private fun isInitVar(field: PyTargetExpression): Boolean {
      return (myTypeEvalContext.getType(field) as? PyClassType)?.classQName == DATACLASSES_INITVAR_TYPE
    }

    private fun isExpectedDataclass(type: PyType?,
                                    dataclassType: PyDataclassParameters.PredefinedType?,
                                    allowDefinition: Boolean,
                                    allowInstance: Boolean,
                                    allowSubclass: Boolean): Boolean {
      if (type is PyStructuralType || PyTypeChecker.isUnknown(type, myTypeEvalContext)) return true
      if (type is PyUnionType) return type.members.any {
        isExpectedDataclass(it, dataclassType, allowDefinition, allowInstance, allowSubclass)
      }

      return type is PyClassType &&
             (allowDefinition || !type.isDefinition) &&
             (allowInstance || type.isDefinition) &&
             (
               parseDataclassParameters(type.pyClass, myTypeEvalContext)?.type?.asPredefinedType == dataclassType ||
               allowSubclass && type.getAncestorTypes(myTypeEvalContext).any {
                 isExpectedDataclass(it, dataclassType, true, false, false)
               }
             )
    }
  }
}
