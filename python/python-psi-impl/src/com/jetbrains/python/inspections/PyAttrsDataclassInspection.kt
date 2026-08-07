package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.resolveDataclassFieldParameters
import com.jetbrains.python.codeInsight.resolvesToOmittedDefault
import com.jetbrains.python.codeInsight.stdlib.PyAttrsDataclassType
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Attrs
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.TypeEvalContext
import kotlin.collections.asSequence
import kotlin.collections.contains
import kotlin.sequences.forEach

class PyAttrsDataclassInspection : PyInspection() {
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
      val dataclassParameters = parseDataclassParameters(node, myTypeEvalContext)?.takeIf { it.type == PyAttrsDataclassType } ?: return

      processAttrsParameters(node, dataclassParameters)

      node
        .findMethodByName(Attrs.DUNDER_POST_INIT, false, myTypeEvalContext)
        ?.also { processAttrsPostInitDefinition(it, dataclassParameters) }

      processAttrsDefaultThroughDecorator(dataclassParameters, node)
      processAttrsInitializersAndValidators(node)
      processAttrIbFunctionCalls(dataclassParameters, node)
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
          in ORDER_OPERATORS,
            -> cmpMethods.add(it)
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

      if (dataclassParameters.frozen == true && mutatingMethods.isNotEmpty()) {
        mutatingMethods.forEach { problems.add(it to "frozen") }
      }

      if (dataclassParameters.unsafeHash && hashMethod != null) {
        problems.add(hashMethod to "hash")
      }

      problems.forEach {
        it.first?.apply {
          registerProblem(nameIdentifier,
                          PyPsiBundle.problemMessage("INSP.dataclasses.method.is.ignored.if.class.already.defines.parameter", name, it.second),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }

      if (dataclassParameters.order && dataclassParameters.frozen == true && hashMethod != null) {
        registerProblem(hashMethod.nameIdentifier,
                        PyPsiBundle.message("INSP.dataclasses.hash.ignored.if.class.already.defines.cmp.or.order.or.frozen.parameters"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }

    private fun processAttrsDefaultThroughDecorator(dataclassParameters: PyDataclassParameters, cls: PyClass) {
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

                val fieldParams = resolveDataclassFieldParameters(cls, dataclassParameters, attribute, myTypeEvalContext)
                if (fieldParams != null && (fieldParams.hasDefault || fieldParams.hasDefaultFactory)) {
                  registerProblem(method.nameIdentifier,
                                  PyPsiBundle.problemMessage("INSP.dataclasses.attribute.default.set.using.method", "${attribute.calleeName}()"),
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
          .forEach {
            registerProblem(it.nameIdentifier,
                            PyPsiBundle.problemMessage("INSP.dataclasses.attribute.default.set.using.method", first.name),
                            ProblemHighlightType.GENERIC_ERROR)
          }
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
                  val message = PyPsiBundle.problemMessage("INSP.dataclasses.method.should.take.only.n.parameter", method.name, expectedParameters)

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

    private fun processAttrIbFunctionCalls(dataclassParameters: PyDataclassParameters, dataclass: PyClass) {
      dataclass.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression) {
          val fieldParams = resolveDataclassFieldParameters(dataclass, dataclassParameters, element, myTypeEvalContext)
          val call = element.findAssignedValue() as? PyCallExpression

          if (call != null && fieldParams != null) {
            if (fieldParams.hasDefaultFactory) {
              if (fieldParams.hasDefault) {
                registerProblem(call.argumentList, PyPsiBundle.message("INSP.dataclasses.cannot.specify.both.default.and.factory"),
                                ProblemHighlightType.GENERIC_ERROR)
              }
              else {
                // at least covers the following case: `attr.ib(default=attr.Factory(...), factory=...)`

                val default = call.getKeywordArgument("default")
                val factory = call.getKeywordArgument("factory")

                if (default != null && factory != null && !PyAttrsDataclassType.resolver.resolvesToOmittedDefault(default)) {
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
  }
}
