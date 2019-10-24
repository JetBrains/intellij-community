// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.generate

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Experimental
interface UastCodeGenerationPlugin {
  companion object {
    val extensionPointName = ExtensionPointName<UastCodeGenerationPlugin>("org.jetbrains.uast.generate.uastCodeGenerationPlugin")
    private val extensions by lazy { extensionPointName.extensionList }

    @JvmStatic
    fun byLanguage(language: Language) = extensions.firstOrNull { it.language == language }
  }

  fun getElementFactory(project: Project): UastElementFactory

  val language: Language

  fun <T : UElement> replace(oldElement: UElement, newElement: T, elementType: Class<T>): T?
}

@ApiStatus.Experimental
interface UastElementFactory {
  fun createBinaryExpression(leftOperand: UExpression, rightOperand: UExpression, operator: UastBinaryOperator): UBinaryExpression?

  /**
   * Create binary expression, and possibly remove unnecessary parenthesis, so it could become [UPolyadicExpression], e.g
   * [createFlatBinaryExpression] (1 + 2, 2, +) could produce 1 + 2 + 2, which is polyadic expression
   */
  @JvmDefault
  fun createFlatBinaryExpression(leftOperand: UExpression, rightOperand: UExpression, operator: UastBinaryOperator): UPolyadicExpression? =
    createBinaryExpression(leftOperand, rightOperand, operator)

  fun createSimpleReference(name: String): USimpleNameReferenceExpression?

  fun createSimpleReference(variable: UVariable): USimpleNameReferenceExpression?

  fun createQualifiedReference(qualifiedName: String, context: UElement?): UQualifiedReferenceExpression?

  fun createParenthesizedExpression(expression: UExpression): UParenthesizedExpression?

  fun createReturnExpresion(expression: UExpression?,
                            inLambda: Boolean = false): UReturnExpression?

  fun createLocalVariable(suggestedName: String?, type: PsiType?, initializer: UExpression, immutable: Boolean = false): ULocalVariable?

  fun createBlockExpression(expressions: List<UExpression>): UBlockExpression?

  fun createLambdaExpression(parameters: List<UParameterInfo>, body: UExpression): ULambdaExpression?

  fun createDeclarationExpression(declarations: List<UDeclaration>): UDeclarationsExpression?

  /**
   * For providing additional information pass it via [context] only, otherwise it can be lost
   */
  fun createCallExpression(receiver: UExpression?,
                           methodName: String,
                           parameters: List<UExpression>,
                           expectedReturnType: PsiType?,
                           kind: UastCallKind,
                           context: PsiElement? = null): UCallExpression?

  fun createIfExpression(condition: UExpression, thenBranch: UExpression, elseBranch: UExpression? = null): UIfExpression?

}

@ApiStatus.Experimental
data class UParameterInfo(val type: PsiType?, val suggestedName: String?)

@ApiStatus.Experimental
infix fun String?.ofType(type: PsiType?): UParameterInfo = UParameterInfo(type, this)

@ApiStatus.Experimental
inline fun <reified T : UElement> UElement.replace(newElement: T): T? =
  UastCodeGenerationPlugin.byLanguage(this.lang)
    ?.replace(this, newElement, T::class.java)

@ApiStatus.Experimental
inline fun <reified T : UElement> T.refreshed() = sourcePsi?.toUElementOfType<T>()

val UElement.generationPlugin: UastCodeGenerationPlugin?
  @ApiStatus.Experimental
  get() = UastCodeGenerationPlugin.byLanguage(this.lang)

@ApiStatus.Experimental
fun UElement.getUastElementFactory(project: Project): UastElementFactory? =
  generationPlugin?.getElementFactory(project)

