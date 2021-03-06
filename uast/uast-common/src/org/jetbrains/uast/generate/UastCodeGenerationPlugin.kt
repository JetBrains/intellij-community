// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.generate

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import kotlin.streams.asSequence

@ApiStatus.Experimental
interface UastCodeGenerationPlugin {
  companion object {
    private val extensionPointName = ExtensionPointName<UastCodeGenerationPlugin>("org.jetbrains.uast.generate.uastCodeGenerationPlugin")

    @JvmStatic
    fun byLanguage(language: Language) = extensionPointName.extensions().asSequence().firstOrNull { it.language == language }
  }

  fun getElementFactory(project: Project): UastElementFactory

  val language: Language

  fun <T : UElement> replace(oldElement: UElement, newElement: T, elementType: Class<T>): T?
}

@ApiStatus.Experimental
interface UastElementFactory {
  fun createBinaryExpression(leftOperand: UExpression, rightOperand: UExpression, operator: UastBinaryOperator,
                             context: PsiElement?): UBinaryExpression?

  /**
   * Create binary expression, and possibly remove unnecessary parenthesis, so it could become [UPolyadicExpression], e.g
   * [createFlatBinaryExpression] (1 + 2, 2, +) could produce 1 + 2 + 2, which is polyadic expression
   */
  @JvmDefault
  fun createFlatBinaryExpression(leftOperand: UExpression,
                                 rightOperand: UExpression,
                                 operator: UastBinaryOperator,
                                 context: PsiElement?): UPolyadicExpression? =
    createBinaryExpression(leftOperand, rightOperand, operator, context)

  fun createSimpleReference(name: String, context: PsiElement?): USimpleNameReferenceExpression?

  fun createSimpleReference(variable: UVariable, context: PsiElement?): USimpleNameReferenceExpression?

  fun createQualifiedReference(qualifiedName: String, context: UElement?): UQualifiedReferenceExpression?

  fun createParenthesizedExpression(expression: UExpression,
                                    context: PsiElement?): UParenthesizedExpression?

  fun createReturnExpresion(expression: UExpression?,
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
   * For providing additional information pass it via [context] only, otherwise it can be lost
   */
  fun createCallExpression(receiver: UExpression?,
                           methodName: String,
                           parameters: List<UExpression>,
                           expectedReturnType: PsiType?,
                           kind: UastCallKind,
                           context: PsiElement? = null): UCallExpression?

  fun createIfExpression(condition: UExpression, thenBranch: UExpression, elseBranch: UExpression?, context: PsiElement?): UIfExpression?

  fun createStringLiteralExpression(text: String, context: PsiElement?): ULiteralExpression?

  fun createNullLiteral(context: PsiElement?): ULiteralExpression?
}

@ApiStatus.Experimental
data class UParameterInfo(val type: PsiType?, val suggestedName: String?)

@ApiStatus.Experimental
infix fun String?.ofType(type: PsiType?): UParameterInfo = UParameterInfo(type, this)

@ApiStatus.Experimental
inline fun <reified T : UElement> UElement.replace(newElement: T): T? =
  UastCodeGenerationPlugin.byLanguage(this.lang)
    ?.replace(this, newElement, T::class.java).also {
      if (it == null) {
        logger<UastCodeGenerationPlugin>().warn("failed replacing the $this with $newElement")
      }
    }

@ApiStatus.Experimental
inline fun <reified T : UElement> T.refreshed() = sourcePsi?.also {
  logger<UastCodeGenerationPlugin>().assertTrue(it.isValid,
                                                "psi $it of class ${it.javaClass} should be valid, containing file = ${it.containingFile}")
}?.toUElementOfType<T>()

val UElement.generationPlugin: UastCodeGenerationPlugin?
  @ApiStatus.Experimental
  get() = UastCodeGenerationPlugin.byLanguage(this.lang)

@ApiStatus.Experimental
fun UElement.getUastElementFactory(project: Project): UastElementFactory? =
  generationPlugin?.getElementFactory(project)

