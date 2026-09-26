// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.parseStdOrDataclassTransformDataclassParameters
import com.jetbrains.python.codeInsight.resolveDataclassFieldParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus

/**
 * Shared base for the `PyDataclassInspection` visitors. It holds only the framework-agnostic *helpers* reused by the
 * per-framework visitors — it intentionally defines no `visitXxx` checks, so a framework visitor inherits nothing that
 * would double-run when the dispatcher fans a callback out to both the common pass and a framework pass.
 *
 * The actual checks live in dedicated visitors: [PyCommonDataclassVisitor] for the framework-agnostic ones and one
 * visitor per framework (e.g. [PyStdlibDataclassVisitor], or an extension-point framework's visitor). `PyDataclassInspection` wires
 * them together: for every callback it first runs the common pass and then, depending on the framework, the matching
 * framework visitor.
 */
@ApiStatus.Internal
open class PyDataclassVisitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

  /**
   * Reports the parameter/method conflicts shared by stdlib and `dataclass_transform` dataclasses (`eq`/`order`
   * consistency, `init`/`repr`/`eq`/`order`/`frozen`/`unsafe_hash` arguments made useless by hand-written methods, and
   * frozen-inheritance consistency across the hierarchy). Invoked by the stdlib and transform visitors.
   */
  protected fun processDataclassParameters(cls: PyClass, dataclassParameters: PyDataclassParameters) {
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
                      PyPsiBundle.problemMessage("INSP.dataclasses.argument.ignored.if.class.already.defines.method", it.second, it.third),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }

    if (dataclassParameters.order && orderMethodsExist) {
      registerProblem(dataclassParameters.orderArgument,
                      PyPsiBundle.message("INSP.dataclasses.order.argument.should.be.false.if.class.defines.one.of.order.methods"),
                      ProblemHighlightType.GENERIC_ERROR)
    }

    if (dataclassParameters.frozen == true && mutatingMethodsExist) {
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
      val currentFrozen = parseStdOrDataclassTransformDataclassParameters(current, myTypeEvalContext)?.frozen ?: continue

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

  /**
   * Reports conflicting `field(...)` arguments (a `default_factory` on a `ClassVar`/`InitVar`, or both `default` and
   * `default_factory`). Shared by the stdlib and transform visitors.
   */
  protected fun processFieldFunctionCall(dataclass: PyClass, dataclassParameters: PyDataclassParameters, field: PyTargetExpression) {
    val fieldStub = resolveDataclassFieldParameters(dataclass, dataclassParameters, field, myTypeEvalContext) ?: return
    val call = field.findAssignedValue() as? PyCallExpression ?: return

    if (PyTypingTypeProvider.isClassVar(field, myTypeEvalContext) || getInitVarType(field) != null) {
      if (fieldStub.hasDefaultFactory) {
        registerProblem(call.getKeywordArgument("default_factory"),
                        PyPsiBundle.message("INSP.dataclasses.field.cannot.have.default.factory"),
                        ProblemHighlightType.GENERIC_ERROR)
      }
    }
    else if (fieldStub.hasDefault && fieldStub.hasDefaultFactory) {
      registerProblem(call.argumentList, PyPsiBundle.message("INSP.dataclasses.cannot.specify.both.default.and.default.factory"),
                      ProblemHighlightType.GENERIC_ERROR)
    }
  }

  /** The `dataclasses.InitVar[...]` element type of [field], or `null` when it is not an InitVar. */
  protected fun getInitVarType(field: PyTargetExpression): PyType? {
    val fieldType = myTypeEvalContext.getType(field)
    if (fieldType is PyCollectionType && fieldType.classQName == Dataclasses.DATACLASSES_INITVAR) {
      return fieldType.elementTypes.singleOrNull()
    }
    return null
  }

  /** The [PyClass] of the instance [element] refers to, or `null` when [element] is a type/definition or not a class. */
  fun getInstancePyClass(element: PyTypedElement?): PyClass? {
    val type = element?.let { myTypeEvalContext.getType(it) } as? PyClassType
    return if (type != null && !type.isDefinition) type.pyClass else null
  }
}

internal val ORDER_OPERATORS: Set<String> = setOf("__lt__", "__le__", "__gt__", "__ge__")
