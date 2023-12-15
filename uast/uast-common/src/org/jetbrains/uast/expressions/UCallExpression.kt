// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a call expression (method/constructor call, array initializer).
 */
@JvmDefaultWithCompatibility
interface UCallExpression : UExpression, UResolvable {
  /**
   * Returns the call kind.
   */
  val kind: UastCallKind

  /**
   * Checks if kind corresponds to the expected kind possibly employing additional performance optimizations.
   */
  fun hasKind(expectedKind: UastCallKind): Boolean {
    return kind == expectedKind
  }

  /**
   * Returns the called method name, or null if the call is not a method call.
   * This property should return the actual resolved function name.
   * The method may be slow, see [isMethodNameOneOf] for more optimized name checking.
   */
  val methodName: String?

  /**
   * Returns the expression receiver.
   * For example, for call `a.b.[c()]` the receiver is `a.b`.
   */
  val receiver: UExpression?

  /**
   * Returns the receiver type, or null if the call has not a receiver.
   */
  val receiverType: PsiType?

  /**
   * Returns the function reference expression if the call is a non-constructor method call, null otherwise.
   */
  val methodIdentifier: UIdentifier?

  /**
   * Returns the class reference if the call is a constructor call, null otherwise.
   */
  val classReference: UReferenceExpression?

  /**
   * Returns the value argument count.
   *
   * Retrieving the argument count could be faster than getting the [valueArguments] size,
   *    because there is no need to create actual [UExpression] instances.
   */
  val valueArgumentCount: Int

  /**
   * Returns the list of value arguments.
   */
  val valueArguments: List<UExpression>

  /**
   * Returns the type argument count.
   */
  val typeArgumentCount: Int

  /**
   * Returns the type arguments for the call.
   */
  val typeArguments: List<PsiType>

  /**
   * Returns the return type of the called function, or null if the call is not a function call.
   */
  val returnType: PsiType?

  /**
   * Resolve the called method.
   *
   * @return the [PsiMethod], or null if the method was not resolved.
   * Note that the [PsiMethod] is an unwrapped [PsiMethod], not a [UMethod].
   */
  override fun resolve(): PsiMethod?

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitCallExpression(this)) return
    uAnnotations.acceptList(visitor)
    methodIdentifier?.accept(visitor)
    classReference?.accept(visitor)
    valueArguments.acceptList(visitor)
    visitor.afterVisitCallExpression(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitCallExpression(this, data)

  override fun asLogString(): String = log("kind = $kind, argCount = $valueArgumentCount)")

  override fun asRenderString(): String {
    val ref = classReference?.asRenderString() ?: methodName ?: methodIdentifier?.asRenderString() ?: "<noref>"
    return ref + "(" + valueArguments.joinToString { it.asRenderString() } + ")"
  }

  /**
   * Provides the ability to match the called method parameters with passed arguments.
   * Useful when the order of passed arguments is different to the order of declared parameters (e.g. in Kotlin named arguments).
   *
   * @see UCallExpression.getParameterForArgument
   *
   * @param i index of the parameter in the resolved [PsiMethod] declaration
   * @return [UExpression] that corresponds to the [i]-th parameter.
   * If the given parameter is vararg then the corresponding arguments will be returned wrapped into
   * [UExpressionList] (with [UExpressionList.kind] = [UastSpecialExpressionKind.VARARGS])
   */
  fun getArgumentForParameter(i: Int): UExpression?

  /**
   * Tries to perform optimized name checking for cases when [methodName] requires reference resolution.
   *
   * May perform some heavy resolution inside for some languages (e.g., for Kotlin).
   *
   * @see methodName
   */
  @ApiStatus.Experimental
  fun isMethodNameOneOf(names: Collection<String>): Boolean {
    return names.contains(methodName ?: return false)
  }

}

@Deprecated("useless since IDEA 2019.2, because getArgumentForParameter moved to UCallExpression", ReplaceWith("UCallExpression"))
interface UCallExpressionEx : UCallExpression