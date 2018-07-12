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
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.stdlib.*
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*

class PyDataclassInspection : PyInspection() {

  companion object {
    private val ORDER_OPERATORS = setOf("__lt__", "__le__", "__gt__", "__ge__")
    private val DATACLASSES_HELPERS = setOf("dataclasses.fields", "dataclasses.asdict", "dataclasses.astuple", "dataclasses.replace")
    private val ATTRS_HELPERS = setOf("attr.__init__.fields",
                                      "attr.__init__.fields_dict",
                                      "attr.__init__.asdict",
                                      "attr.__init__.astuple",
                                      "attr.__init__.assoc",
                                      "attr.__init__.evolve")
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyTargetExpression(node: PyTargetExpression?) {
      super.visitPyTargetExpression(node)

      if (node != null) checkMutatingFrozenAttribute(node)
    }

    override fun visitPyDelStatement(node: PyDelStatement?) {
      super.visitPyDelStatement(node)

      if (node != null) {
        node
          .targets
          .asSequence()
          .filterIsInstance<PyReferenceExpression>()
          .forEach { checkMutatingFrozenAttribute(it) }
      }
    }

    override fun visitPyClass(node: PyClass?) {
      super.visitPyClass(node)

      if (node != null) {
        val dataclassParameters = parseDataclassParameters(node, myTypeEvalContext)

        if (dataclassParameters != null) {
          if (dataclassParameters.type == PyDataclassParameters.Type.STD) {
            processDataclassParameters(node, dataclassParameters)

            val postInit = node.findMethodByName(DUNDER_POST_INIT, false, myTypeEvalContext)
            val initVars = mutableListOf<PyTargetExpression>()

            node.processClassLevelDeclarations { element, _ ->
              if (element is PyTargetExpression) {
                if (!PyTypingTypeProvider.isClassVar(element, myTypeEvalContext)) {
                  processDefaultFieldValue(element)
                  processAsInitVar(element, postInit)?.let { initVars.add(it) }
                }

                processFieldFunctionCall(element)
              }

              true
            }

            if (postInit != null) {
              processPostInitDefinition(postInit, dataclassParameters, initVars)
            }
          }
          else if (dataclassParameters.type == PyDataclassParameters.Type.ATTRS) {
            processAttrsParameters(node, dataclassParameters)

            node
              .findMethodByName(DUNDER_ATTRS_POST_INIT, false, myTypeEvalContext)
              ?.also { processAttrsPostInitDefinition(it, dataclassParameters) }

            processAttrsDefaultThroughDecorator(node)
            processAttrsInitializersAndValidators(node)
            processAttrsAutoAttribs(node, dataclassParameters)
            processAttrIbFunctionCalls(node)
          }

          PyNamedTupleInspection.inspectFieldsOrder(
            node,
            this::registerProblem,
            { !PyTypingTypeProvider.isClassVar(it, myTypeEvalContext) },
            {
              val fieldStub = PyDataclassFieldStubImpl.create(it)

              if (fieldStub != null) {
                fieldStub.hasDefault() ||
                fieldStub.hasDefaultFactory() ||
                dataclassParameters.type == PyDataclassParameters.Type.ATTRS &&
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
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression?) {
      super.visitPyBinaryExpression(node)

      if (node != null && ORDER_OPERATORS.contains(node.referencedName)) {
        val leftClass = getInstancePyClass(node.leftExpression) ?: return
        val rightClass = getInstancePyClass(node.rightExpression) ?: return

        val leftDataclassParameters = parseDataclassParameters(leftClass, myTypeEvalContext)

        if (leftClass != rightClass &&
            leftDataclassParameters != null &&
            parseDataclassParameters(rightClass, myTypeEvalContext) != null) {
          registerProblem(node.psiOperator,
                          "'${node.referencedName}' not supported between instances of '${leftClass.name}' and '${rightClass.name}'",
                          ProblemHighlightType.GENERIC_ERROR)
        }

        if (leftClass == rightClass && leftDataclassParameters?.order == false) {
          registerProblem(node.psiOperator,
                          "'${node.referencedName}' not supported between instances of '${leftClass.name}'",
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression?) {
      super.visitPyCallExpression(node)

      if (node != null) {
        val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext)
        val markedCallee = node.multiResolveCallee(resolveContext).singleOrNull()
        val callee = markedCallee?.element
        val calleeQName = callee?.qualifiedName

        if (markedCallee != null && callee != null) {
          val dataclassType = when {
            DATACLASSES_HELPERS.contains(calleeQName) -> PyDataclassParameters.Type.STD
            ATTRS_HELPERS.contains(calleeQName) -> PyDataclassParameters.Type.ATTRS
            else -> return
          }

          val mapping = PyCallExpressionHelper.mapArguments(node, markedCallee, myTypeEvalContext)

          val dataclassParameter = callee.getParameters(myTypeEvalContext).firstOrNull()
          val dataclassArgument = mapping.mappedParameters.entries.firstOrNull { it.value == dataclassParameter }?.key

          if (dataclassType == PyDataclassParameters.Type.STD) {
            processHelperDataclassArgument(dataclassArgument, calleeQName!!)
          }
          else if (dataclassType == PyDataclassParameters.Type.ATTRS) {
            processHelperAttrsArgument(dataclassArgument, calleeQName!!)
          }
        }
      }
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression?) {
      super.visitPyReferenceExpression(node)

      if (node != null && node.isQualified) {
        val cls = getInstancePyClass(node.qualifier) ?: return

        if (parseStdDataclassParameters(cls, myTypeEvalContext) != null) {
          cls.processClassLevelDeclarations { element, _ ->
            if (element is PyTargetExpression && element.name == node.name && isInitVar(element)) {
              registerProblem(node.lastChild,
                              "'${cls.name}' object could have no attribute '${element.name}' because it is declared as init-only",
                              ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

              return@processClassLevelDeclarations false
            }

            true
          }
        }
      }
    }

    private fun checkMutatingFrozenAttribute(expression: PyQualifiedExpression) {
      val cls = getInstancePyClass(expression.qualifier) ?: return
      if (parseDataclassParameters(cls, myTypeEvalContext)?.frozen == true) {
        registerProblem(expression, "'${cls.name}' object attribute '${expression.name}' is read-only", ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun getInstancePyClass(element: PyTypedElement?): PyClass? {
      val type = element?.let { myTypeEvalContext.getType(it) } as? PyClassType
      return if (type != null && !type.isDefinition) type.pyClass else null
    }

    private fun processDataclassParameters(cls: PyClass, dataclassParameters: PyDataclassParameters) {
      if (!dataclassParameters.eq && dataclassParameters.order) {
        registerProblem(dataclassParameters.eqArgument, "'eq' must be true if 'order' is true", ProblemHighlightType.GENERIC_ERROR)
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
                        "'${it.second}' is ignored if the class already defines '${it.third}' method",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }

      if (dataclassParameters.order && orderMethodsExist) {
        registerProblem(dataclassParameters.orderArgument,
                        "'order' should be false if the class defines one of order methods",
                        ProblemHighlightType.GENERIC_ERROR)
      }

      if (dataclassParameters.frozen && mutatingMethodsExist) {
        registerProblem(dataclassParameters.frozenArgument,
                        "'frozen' should be false if the class defines '__setattr__' or '__delattr__'",
                        ProblemHighlightType.GENERIC_ERROR)
      }

      if (dataclassParameters.unsafeHash && hashMethodExists) {
        registerProblem(dataclassParameters.unsafeHashArgument,
                        "'unsafe_hash' should be false if the class defines '${PyNames.HASH}'",
                        ProblemHighlightType.GENERIC_ERROR)
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
        cmpMethods.forEach { problems.add(it to "cmp") }
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
                          "'$name' is ignored if the class already defines '${it.second}' parameter",
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }

      if (dataclassParameters.order && dataclassParameters.frozen && hashMethod != null) {
        registerProblem(hashMethod?.nameIdentifier,
                        "'${PyNames.HASH}' is ignored if the class already defines 'cmp' and 'frozen' parameters",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }

    private fun processDefaultFieldValue(field: PyTargetExpression) {
      if (field.annotationValue == null) return

      val value = field.findAssignedValue()
      if (PyUtil.isForbiddenMutableDefault(value, myTypeEvalContext)) {
        registerProblem(value,
                        "Mutable default '${value?.text}' is not allowed. Use 'default_factory'",
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
                initializers.computeIfAbsent(name, { _ -> mutableListOf() }).add(method)

                val stub = PyDataclassFieldStubImpl.create(attribute)
                if (stub != null && (stub.hasDefault() || stub.hasDefaultFactory())) {
                  registerProblem(method.nameIdentifier, "A default is set using 'attr.ib()'", ProblemHighlightType.GENERIC_ERROR)
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
          .forEach { registerProblem(it.nameIdentifier, "A default is set using '${first.name}'", ProblemHighlightType.GENERIC_ERROR) }
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
                  val message = "'${method.name}' should take only $expectedParameters parameter" +
                                if (expectedParameters > 1) "s" else ""

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

    private fun processAttrsAutoAttribs(cls: PyClass, dataclassParameters: PyDataclassParameters) {
      if (PyEvaluator.evaluateAsBoolean(PyUtil.peelArgument(dataclassParameters.others["auto_attribs"]), false)) {
        cls.processClassLevelDeclarations { element, _ ->
          if (element is PyTargetExpression && element.annotation == null && PyDataclassFieldStubImpl.create(element) != null) {
            registerProblem(element, "Attribute '${element.name}' lacks a type annotation", ProblemHighlightType.GENERIC_ERROR)
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
                registerProblem(call.argumentList, "Cannot specify both 'default' and 'factory'", ProblemHighlightType.GENERIC_ERROR)
              }
              else {
                // at least covers the following case: `attr.ib(default=attr.Factory(...), factory=...)`

                val default = call.getKeywordArgument("default")
                val factory = call.getKeywordArgument("factory")

                if (default != null && factory != null && !resolvesToOmittedDefault(default, PyDataclassParameters.Type.ATTRS)) {
                  registerProblem(call.argumentList, "Cannot specify both 'default' and 'factory'", ProblemHighlightType.GENERIC_ERROR)
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
                          "Attribute '${field.name}' is useless until '$DUNDER_POST_INIT' is declared",
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
                          "Field cannot have a default factory",
                          ProblemHighlightType.GENERIC_ERROR)
        }
      }
      else if (fieldStub.hasDefault() && fieldStub.hasDefaultFactory()) {
        registerProblem(call.argumentList, "Cannot specify both 'default' and 'default_factory'", ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun processPostInitDefinition(postInit: PyFunction,
                                          dataclassParameters: PyDataclassParameters,
                                          initVars: List<PyTargetExpression>) {
      if (!dataclassParameters.init) {
        registerProblem(postInit.nameIdentifier,
                        "'$DUNDER_POST_INIT' would not be called until 'init' parameter is set to True",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      }

      val parameters = ContainerUtil.subList(postInit.getParameters(myTypeEvalContext), 1)
      val message = "'$DUNDER_POST_INIT' should take all init-only variables in the same order as they are defined"

      if (parameters.size != initVars.size) {
        registerProblem(postInit.parameterList, message, ProblemHighlightType.GENERIC_ERROR)
      }
      else {
        parameters
          .asSequence()
          .zip(initVars.asSequence())
          .all { it.first.name == it.second.name }
          .also { if (!it) registerProblem(postInit.parameterList, message) }
      }
    }

    private fun processAttrsPostInitDefinition(postInit: PyFunction, dataclassParameters: PyDataclassParameters) {
      if (!dataclassParameters.init) {
        registerProblem(postInit.nameIdentifier,
                        "'$DUNDER_ATTRS_POST_INIT' would not be called until 'init' parameter is set to True",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      }

      if (postInit.getParameters(myTypeEvalContext).size != 1) {
        registerProblem(postInit.parameterList,
                        "'$DUNDER_ATTRS_POST_INIT' should not take any parameters except '${PyNames.CANONICAL_SELF}'",
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }

    private fun processHelperDataclassArgument(argument: PyExpression?, calleeQName: String) {
      if (argument == null) return

      val allowDefinition = calleeQName == "dataclasses.fields"

      if (isNotExpectedDataclass(myTypeEvalContext.getType(argument), PyDataclassParameters.Type.STD, allowDefinition, true)) {
        val message = "'$calleeQName' method should be called on dataclass instances" + if (allowDefinition) " or types" else ""

        registerProblem(argument, message)
      }
    }

    private fun processHelperAttrsArgument(argument: PyExpression?, calleeQName: String) {
      if (argument == null) return

      val instance = calleeQName != "attr.__init__.fields" && calleeQName != "attr.__init__.fields_dict"

      if (isNotExpectedDataclass(myTypeEvalContext.getType(argument), PyDataclassParameters.Type.ATTRS, !instance, instance)) {
        val presentableCalleeQName = calleeQName.replaceFirst(".__init__.", ".")
        val message = "'$presentableCalleeQName' method should be called on attrs " + if (instance) "instances" else "types"

        registerProblem(argument, message)
      }
    }

    private fun isInitVar(field: PyTargetExpression): Boolean {
      return (myTypeEvalContext.getType(field) as? PyClassType)?.classQName == DATACLASSES_INITVAR_TYPE
    }

    private fun isNotExpectedDataclass(type: PyType?,
                                       dataclassType: PyDataclassParameters.Type,
                                       allowDefinition: Boolean,
                                       allowInstance: Boolean): Boolean {
      if (type is PyStructuralType || PyTypeChecker.isUnknown(type, myTypeEvalContext)) return false
      if (type is PyUnionType) return type.members.all { isNotExpectedDataclass(it, dataclassType, allowDefinition, allowInstance) }

      return type !is PyClassType ||
             !allowDefinition && type.isDefinition ||
             !allowInstance && !type.isDefinition ||
             parseDataclassParameters(type.pyClass, myTypeEvalContext)?.type != dataclassType
    }
  }
}