package com.jetbrains.python.codeInsight.dataflow.scope

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubBuildCachedValuesManager.StubBuildCachedValueProvider
import com.intellij.psi.stubs.StubBuildCachedValuesManager.getCachedValueStubBuildOptimized
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.ast.PyAstClass
import com.jetbrains.python.ast.PyAstDecorator
import com.jetbrains.python.ast.PyAstDecoratorList
import com.jetbrains.python.ast.PyAstElement
import com.jetbrains.python.ast.PyAstExpressionCodeFragment
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.ast.PyAstLambdaExpression
import com.jetbrains.python.ast.PyAstNamedParameter
import com.jetbrains.python.ast.controlFlow.AstScopeOwner
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object ScopeUtilCore {
  /**
   * Return the scope owner for the element.
   *
   * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
   *
   * This method does not access AST if underlying PSI is stub based.
   */
  @JvmStatic
  fun getScopeOwner(element: PsiElement?): AstScopeOwner? {
    if (element == null) {
      return null
    }
    if (element is PyAstExpressionCodeFragment) {
      val context = element.context
      return context as? AstScopeOwner ?: getScopeOwner(context)
    }
    return getCachedValueStubBuildOptimized(element, GET_SCOPE_OWNER_PROVIDER)
  }

  private val GET_SCOPE_OWNER_PROVIDER = StubBuildCachedValueProvider<AstScopeOwner?, PsiElement>(
    "python.scopeOwner",
    ParameterizedCachedValueProvider { element: PsiElement? ->
      CachedValueProvider.Result
        .create<AstScopeOwner?>(calculateScopeOwner(element), PsiModificationTracker.MODIFICATION_COUNT)
    }
  )

  private fun calculateScopeOwner(element: PsiElement?): AstScopeOwner? {
    if (element is StubBasedPsiElement<*>) {
      val stub = element.getStub()
      if (stub != null) {
        val firstOwner = stub.getParentStubOfType(AstScopeOwner::class.java)
        val nextOwner: AstScopeOwner?
        if (firstOwner != null && firstOwner !is PsiFile) {
          val firstOwnerStub = checkNotNull((firstOwner as StubBasedPsiElementBase<*>).getGreenStub())
          nextOwner = firstOwnerStub.getParentStubOfType(AstScopeOwner::class.java)
        }
        else {
          nextOwner = null
        }
        if (stub.getParentStubOfType(PyAstDecoratorList::class.java) != null) {
          return nextOwner
        }
        return firstOwner
      }
    }
    val firstOwner = PsiTreeUtil.getParentOfType(element, AstScopeOwner::class.java)
    if (firstOwner == null) {
      return null
    }
    val nextOwner = PsiTreeUtil.getParentOfType(firstOwner, AstScopeOwner::class.java)
    // References in decorator expressions are resolved outside of the function (if the lambda is not inside the decorator)
    val decoratorAncestor: PyAstElement? = PsiTreeUtil.getParentOfType(element, PyAstDecorator::class.java, false)
    if (decoratorAncestor != null && !PsiTreeUtil.isAncestor(decoratorAncestor, firstOwner, true)) {
      return nextOwner
    }
    /*
 * References in default values are resolved outside of the function (if the lambda is not inside the default value).
 * Annotations of parameters are resolved outside of the function if the function doesn't have type parameters list
 */
    val parameterAncestor = PsiTreeUtil.getParentOfType(element, PyAstNamedParameter::class.java, false)
    if (parameterAncestor != null && !PsiTreeUtil.isAncestor(parameterAncestor, firstOwner, true)) {
      val defaultValue = parameterAncestor.defaultValue
      val annotation = parameterAncestor.annotation
      if (firstOwner is PyAstFunction) {
        val typeParameterList = firstOwner.typeParameterList
        if ((typeParameterList == null && PsiTreeUtil.isAncestor(annotation, element!!, false))
            || (PsiTreeUtil.isAncestor(defaultValue, element!!, false))
        ) {
          return nextOwner
        }
      }
      else if (firstOwner is PyAstLambdaExpression && PsiTreeUtil.isAncestor(defaultValue, element!!, false)) {
        return nextOwner
      }
    }
    // Superclasses are resolved outside of the class if the class doesn't have type parameters list
    val containingClass = PsiTreeUtil.getParentOfType(element, PyAstClass::class.java)
    if (containingClass != null && PsiTreeUtil.isAncestor(
        containingClass.superClassExpressionList,
        element!!,
        false
      ) && containingClass.typeParameterList == null
    ) {
      return nextOwner
    }
    // Function return annotations and type comments are resolved outside of the function if the function doesn't have type parameters list
    if (firstOwner is PyAstFunction) {
      val typeParameterList = firstOwner.typeParameterList
      if ((typeParameterList == null && PsiTreeUtil.isAncestor(firstOwner.annotation, element!!, false)
           || PsiTreeUtil.isAncestor(firstOwner.typeComment, element!!, false))
      ) {
        return nextOwner
      }
    }
    return firstOwner
  }
}
