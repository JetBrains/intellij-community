// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.generate

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

/**
 * Extensions which provides code generation support for generating UAST expressions.
 *
 * @see org.jetbrains.uast.UastLanguagePlugin
 */
@ApiStatus.Experimental
interface UastCodeGenerationPlugin {
  companion object {
    private val extensionPointName = ExtensionPointName<UastCodeGenerationPlugin>("org.jetbrains.uast.generate.uastCodeGenerationPlugin")

    @JvmStatic
    fun byLanguage(language: Language): UastCodeGenerationPlugin? = extensionPointName.extensionList.firstOrNull { it.language == language }
  }

  /**
   * @return An element factory that allows generating various UAST expressions.
   */
  fun getElementFactory(project: Project): UastElementFactory

  /**
   * The underlying programming language.
   */
  val language: Language

  /**
   * Replaces a [UElement] by another [UElement] and automatically shortens the reference, if any.
   */
  fun <T : UElement> replace(oldElement: UElement, newElement: T, elementType: Class<T>): T?

  /**
   * Changes the reference so that it starts to point to the specified element. This is called,
   * for example, by the "Create Class from New" quickfix, to bind the (invalid) reference on
   * which the quickfix was called to the newly created class.
   *
   * @param reference the reference to rebind
   * @param element the element which should become the target of the reference.
   * @return the new underlying element of the reference.
   */
  fun bindToElement(reference: UReferenceExpression, element: PsiElement): PsiElement?

  /**
   * Replaces fully-qualified class names in the contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * Example:
   * ```
   * com.jetbrains.uast.generate.UastCodeGenerationPlugin.byLanguage(...)
   * ```
   * Becomes:
   * ```
   * import com.jetbrains.uast.generate.UastCodeGenerationPlugin
   *
   * UastCodeGenerationPlugin.byLanguage(...)
   * ```
   *
   * @param reference the element to shorten references in.
   * @return the element after the shorten references operation corresponding to the original element.
   */
  fun shortenReference(reference: UReferenceExpression): UReferenceExpression?

  /**
   * Import the qualifier of the specified element as an on demand import (star import).
   *
   * Example:
   * ```
   * UastCodeGenerationPlugin.byLanguage(...)
   * ```
   * Becomes:
   * ```
   * import com.jetbrains.uast.generate.UastCodeGenerationPlugin.*
   *
   * byLanguage(...)
   * ```
   *
   * @param reference the qualified element to import
   * @return the selector part of the qualified reference after importing
   */
  fun importMemberOnDemand(reference: UQualifiedReferenceExpression): UExpression?

  /**
   * Initialize the given field with the given parameter inside method's body of the given parameter.
   * If the parameter is from Kotlin primary constructor and the field and the parameter have the same names,
   * field declaration is moved to the primary constructor.
   * If the parameter is from Kotlin primary constructor and the field and the parameter have different names,
   * Kotlin property is initialized with the parameter.
   */
  fun initializeField(uField: UField, uParameter: UParameter, anchor: PsiElement? = null, addBefore: Boolean = false): UExpression?
  
  /**
   * Creates new return expression with changed return label for Explicit return expression (for Kotlin)
   *
   * Example:
   * ```
   * return@map { ... }
   * ```
   * Becomes:
   * ```
   * return@handle { ... }
   * ```
   * @param returnExpression the initial return expression
   * @param context new context in which return is used (label is calculated due to this context)
   * @return new return expression with changed label if return is explicit, otherwise same expression if return is implicit 
   */
  fun changeLabel(returnExpression: UReturnExpression, context: PsiElement) : UReturnExpression

  /**
   * Retrieves the comments associated with the given UElement and restore after modifications.
   *
   * Example:
   * ```
   * if(a()){
   *  //some comments
   *  doSomething();
   * }
   * ```
   * Becomes after grabbing and restoring comments:
   * ```
   * //some comments
   * doSomething();
   * ```
   * The process includes three steps:
   * - grab comments (collect comments from the range)
   * - mark unchanged comments (optional step. Comments, which are not changed, should be marked and they will not be restored)
   * - restore comments after modification (Usually comments will be restored before anchors)
   *
   * The implementation and places for new comments can be different for different languages and rely on language plugins
   *
   * @return The UastCommentSaver containing the comments associated with the UElement, null if it is impossible to create
   */
  fun grabComments(firstResultUElement: UElement, lastResultUElement: UElement = firstResultUElement): UastCommentSaver? = null
}

/**
 * UAST element factory for UAST expressions.
 *
 * @see com.intellij.lang.jvm.actions.JvmElementActionsFactory for more complex JVM based code generation.
 */
@ApiStatus.Experimental
interface UastElementFactory {
  fun createBinaryExpression(leftOperand: UExpression, rightOperand: UExpression, operator: UastBinaryOperator,
                             context: PsiElement?): UBinaryExpression?

  /**
   * Create binary expression, and possibly remove unnecessary parenthesis, so it could become [UPolyadicExpression], e.g
   * [createFlatBinaryExpression] (1 + 2, 2, +) could produce 1 + 2 + 2, which is polyadic expression
   */
  fun createFlatBinaryExpression(leftOperand: UExpression,
                                 rightOperand: UExpression,
                                 operator: UastBinaryOperator,
                                 context: PsiElement?): UPolyadicExpression? =
    createBinaryExpression(leftOperand, rightOperand, operator, context)

  fun createSimpleReference(name: String, context: PsiElement?): USimpleNameReferenceExpression?

  fun createSimpleReference(variable: UVariable, context: PsiElement?): USimpleNameReferenceExpression?

  fun createQualifiedReference(qualifiedName: String, context: PsiElement?): UQualifiedReferenceExpression?

  /**
   * Generate method from language-specific text. It's up to the caller to generate the text properly
   * May return null if not implemented by a specific plugin.
   */
  fun createMethodFromText(methodText: String, context: PsiElement?): UMethod? = null

  fun createParenthesizedExpression(expression: UExpression,
                                    context: PsiElement?): UParenthesizedExpression?

  fun createReturnExpression(expression: UExpression?,
                             inLambda: Boolean = false,
                             context: PsiElement?): UReturnExpression?

  fun createLocalVariable(suggestedName: String?,
                          type: PsiType?,
                          initializer: UExpression,
                          immutable: Boolean = false,
                          context: PsiElement?): ULocalVariable?

  fun createBlockExpression(expressions: List<UExpression>, context: PsiElement?): UBlockExpression?

  fun createLambdaExpression(parameters: List<UParameterInfo>, body: UExpression, context: PsiElement?): ULambdaExpression?

  fun createDeclarationExpression(declarations: List<UDeclaration>, context: PsiElement?): UDeclarationsExpression?

  /**
   * For providing additional information pass it via [context] only, otherwise it can be lost.
   * It is not guaranteed, that [receiver] will be part of returned [UCallExpression].
   * If its necessary, use [getQualifiedParentOrThis].
   */
  fun createCallExpression(receiver: UExpression?,
                           methodName: String,
                           parameters: List<UExpression>,
                           expectedReturnType: PsiType?,
                           kind: UastCallKind,
                           context: PsiElement? = null): UCallExpression?

  fun createCallableReferenceExpression(receiver: UExpression?, methodName: String, context: PsiElement?): UCallableReferenceExpression?

  fun createIfExpression(condition: UExpression, thenBranch: UExpression, elseBranch: UExpression?, context: PsiElement?): UIfExpression?

  fun createStringLiteralExpression(text: String, context: PsiElement?): UExpression?

  fun createLongConstantExpression(long: Long, context: PsiElement?): UExpression?

  fun createNullLiteral(context: PsiElement?): ULiteralExpression?

  fun createComment(text: String, context: PsiElement?): UComment
}

@ApiStatus.Experimental
data class UParameterInfo(val type: PsiType?, val suggestedName: String?)

@ApiStatus.Experimental
infix fun String?.ofType(type: PsiType?): UParameterInfo = UParameterInfo(type, this)

@ApiStatus.Experimental
inline fun <reified T : UElement> UElement.replace(newElement: T): T? {
  if (this == newElement) return newElement

  return UastCodeGenerationPlugin.byLanguage(this.lang)
    ?.replace(this, newElement, T::class.java).also {
      if (it == null) {
        logger<UastCodeGenerationPlugin>().warn("failed replacing the $this with $newElement")
      }
    }
}


fun UReferenceExpression.bindToElement(element: PsiElement): PsiElement? =
  UastCodeGenerationPlugin.byLanguage(this.lang)?.bindToElement(this, element)

fun UReferenceExpression.shortenReference(): UReferenceExpression? =
  UastCodeGenerationPlugin.byLanguage(this.lang)?.shortenReference(this)

fun UQualifiedReferenceExpression.importMemberOnDemand(): UExpression? =
  UastCodeGenerationPlugin.byLanguage(this.lang)?.importMemberOnDemand(this)

@ApiStatus.Experimental
inline fun <reified T : UElement> T.refreshed(): T? = sourcePsi?.also {
  logger<UastCodeGenerationPlugin>().assertTrue(it.isValid,
    "psi $it of class ${it.javaClass} should be valid, containing file = ${it.containingFile}")
}?.toUElementOfType<T>()

val UElement.generationPlugin: UastCodeGenerationPlugin?
  @ApiStatus.Experimental
  get() = UastCodeGenerationPlugin.byLanguage(this.lang)

@ApiStatus.Experimental
fun UElement.getUastElementFactory(project: Project): UastElementFactory? =
  generationPlugin?.getElementFactory(project)

/**
 * Represents an interface for restoring comments which are not included in resultUElements.
 */
@ApiStatus.Experimental
interface UastCommentSaver {
  /**
   * Restore comments.
   * Given range of elements is used as an anchor
   * and can be used to calculate what comments should be restored.
   * This method can be called only once.
   */
  fun restore(firstResultUElement: UElement, lastResultUElement: UElement = firstResultUElement)

  /**
   * Marks comments inside the given range of UElements as unchanged.
   * These comments will not be restored
   */
  fun markUnchanged(firstResultUElement: UElement?, lastResultUElement: UElement? = firstResultUElement)
}