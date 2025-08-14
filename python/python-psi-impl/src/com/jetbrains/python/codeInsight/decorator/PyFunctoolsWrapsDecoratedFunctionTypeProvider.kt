package com.jetbrains.python.codeInsight.decorator

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyFunctoolsWrapsDecoratorStub
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Infer type for reference of a function decorated with 'functools.wraps'.
 * Has to be used before {@link com.jetbrains.python.codeInsight.decorator.PyDecoratedFunctionTypeProvider}
 */
class PyFunctoolsWrapsDecoratedFunctionTypeProvider : PyTypeProviderBase() {
  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    if (referenceTarget !is PyFunction) return null
    val wrappedFunction = ContainerUtil.findInstance(resolveWrapped(referenceTarget, context), PyFunction::class.java) ?: return null
    return Ref.create(context.getType(wrappedFunction))
  }

  override fun getCallableType(callable: PyCallable, context: TypeEvalContext): PyType? {
    return Ref.deref(getReferenceType(callable, context, null))
  }

  private fun resolveWrapped(function: PyFunction, context: TypeEvalContext): List<PsiElement> {
    val decorator = function.decoratorList?.decorators?.find {
      val qName = it.qualifiedName
      qName != null && PyKnownDecoratorUtil.asKnownDecorators(qName).contains(PyKnownDecorator.FUNCTOOLS_WRAPS)
    } ?: return emptyList()
    return StubAwareComputation.on(decorator)
      .withCustomStub { it.getCustomStub(PyFunctoolsWrapsDecoratorStub::class.java) }
      .overStub {
        if (it == null) return@overStub emptyList()
        val scopeOwner = ScopeUtil.getScopeOwner(decorator)
        val wrappedQName = QualifiedName.fromDottedString(it.wrapped)
        PyResolveUtil.resolveQualifiedNameInScope(wrappedQName, scopeOwner!!, context)
      }
      .overAst {
        val wrappedExpr = it.argumentList?.getValueExpressionForParam(PyKnownDecoratorUtil.FunctoolsWrapsParameters.WRAPPED)
        if (wrappedExpr == null)
          emptyList()
        else
          PyUtil.multiResolveTopPriority(wrappedExpr, PyResolveContext.defaultContext(context))
      }
      .withStubBuilder(PyFunctoolsWrapsDecoratorStub::create)
      .compute(context)
  }
}