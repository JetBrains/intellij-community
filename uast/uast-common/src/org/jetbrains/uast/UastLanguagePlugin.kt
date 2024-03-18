// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.classSetOf

/**
 * Extension to provide UAST (Unified Abstract Syntax Tree) language support. UAST is an abstraction layer on PSI of different JVM
 * languages. It provides a unified API for working with common language elements like classes and method declarations, literal values and
 * control flow operators.
 *
 * @see org.jetbrains.uast.generate.UastCodeGenerationPlugin for UAST code generation.
 */
@JvmDefaultWithCompatibility
interface UastLanguagePlugin {
  companion object {
    val extensionPointName: ExtensionPointName<UastLanguagePlugin> = ExtensionPointName("org.jetbrains.uast.uastLanguagePlugin")

    fun getInstances(): Collection<UastLanguagePlugin> = extensionPointName.extensionList

    fun byLanguage(language: Language): UastLanguagePlugin? = UastFacade.findPlugin(language)
  }

  data class ResolvedMethod(val call: UCallExpression, val method: PsiMethod)
  data class ResolvedConstructor(val call: UCallExpression, val constructor: PsiMethod, val clazz: PsiClass)

  /**
   * The underlying programming language.
   */
  val language: Language

  /**
   * Checks if the file with the given [fileName] is supported.
   *
   * @param fileName the source file name.
   * @return true, if the file is supported by this converter, false otherwise.
   */
  fun isFileSupported(fileName: String): Boolean

  /**
   * Returns the converter priority. Might be positive, negative or 0 (Java's is 0).
   * UastConverter with the higher priority will be queried earlier.
   *
   * Priority is useful when a language N wraps its own elements (NElement) to, for example, Java's PsiElements,
   *  and Java resolves the reference to such wrapped PsiElements, not the original NElement.
   * In this case, N implementation can handle such wrappers in UastConverter earlier than Java's converter,
   *  so N language converter will have a higher priority.
   */
  val priority: Int

  /**
   * Converts a PSI element, the parent of which already has an UAST representation, to UAST.
   *
   * @param element the element to convert
   * @param parent the parent as an UAST element, or null if the element is a file
   * @param requiredType the expected type of the result.
   * @return the converted element, or null if the element isn't supported or doesn't match the required result type.
   */
  fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>? = null): UElement?

  /**
   * Converts a PSI element, along with its chain of parents, to UAST.
   *
   * @param element the element to convert
   * @param requiredType the expected type of the result.
   * @return the converted element, or null if the element isn't supported or doesn't match the required result type.
   */
  fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement?

  fun getMethodCallExpression(element: PsiElement, containingClassFqName: String?, methodName: String): ResolvedMethod?

  fun getConstructorCallExpression(element: PsiElement, fqName: String): ResolvedConstructor?

  fun getMethodBody(element: PsiMethod): UExpression? {
    if (element is UMethod) return element.uastBody
    return (convertElementWithParent(element, null) as? UMethod)?.uastBody
  }

  fun getInitializerBody(element: PsiClassInitializer): UExpression {
    if (element is UClassInitializer) return element.uastBody
    return (convertElementWithParent(element, null) as? UClassInitializer)?.uastBody ?: UastEmptyExpression(null)
  }

  fun getInitializerBody(element: PsiVariable): UExpression? {
    if (element is UVariable) return element.uastInitializer
    return (convertElementWithParent(element, null) as? UVariable)?.uastInitializer
  }

  fun getContainingAnnotationEntry(uElement: UElement?, annotationsHint: Collection<String>): Pair<UAnnotation, String?>? {
    return getContainingUAnnotationEntry(uElement)
  }

  private fun getContainingUAnnotationEntry(uElement: UElement?): Pair<UAnnotation, String?>? {
    fun tryConvertToEntry(uElement: UElement, parent: UElement, name: String?): Pair<UAnnotation, String?>? {
      if (uElement !is UExpression) return null
      val uAnnotation = parent.sourcePsi.toUElementOfType<UAnnotation>() ?: return null
      val argumentSourcePsi = uElement.sourcePsi
      return uAnnotation to (name ?: uAnnotation.attributeValues.find { it.expression.sourcePsi === argumentSourcePsi }?.name)
    }

    tailrec fun retrievePsiAnnotationEntry(uElement: UElement?, name: String?): Pair<UAnnotation, String?>? {
      if (uElement == null) return null
      val parent = uElement.uastParent ?: return null
      return when (parent) {
        is UAnnotation -> parent to name
        is UReferenceExpression -> tryConvertToEntry(uElement, parent, name)
        is UCallExpression ->
          if (parent.hasKind(UastCallKind.NESTED_ARRAY_INITIALIZER))
            retrievePsiAnnotationEntry(parent, null)
          else
            tryConvertToEntry(uElement, parent, name)
        is UPolyadicExpression -> retrievePsiAnnotationEntry(parent, null)
        is UNamedExpression -> retrievePsiAnnotationEntry(parent, parent.name)
        else -> null
      }
    }

    return retrievePsiAnnotationEntry(uElement, null)
  }

  /**
   * Returns true if the expression value is used.
   * Do not rely on this property too much, its value can be approximate in some cases.
   */
  fun isExpressionValueUsed(element: UExpression): Boolean

  @Suppress("UNCHECKED_CAST")
  fun <T : UElement> convertElementWithParent(element: PsiElement, requiredTypes: Array<out Class<out T>>): T? =
    when {
      requiredTypes.isEmpty() -> convertElementWithParent(element, null)
      requiredTypes.size == 1 -> convertElementWithParent(element, requiredTypes.single())
      else -> convertElementWithParent(element, null)
        ?.takeIf { result -> requiredTypes.any { it.isAssignableFrom(result.javaClass) } }
    } as? T


  fun <T : UElement> convertToAlternatives(element: PsiElement, requiredTypes: Array<out Class<out T>>): Sequence<T> {
    val result = convertElementWithParent(element, requiredTypes)
    return if (result == null) emptySequence() else sequenceOf(result)
  }

  val analysisPlugin: UastAnalysisPlugin?
    @ApiStatus.Experimental
    get() = null

  /**
   * Serves for optimization purposes. Helps to filter PSI elements which in principle
   * can be sources for UAST types of an interest.
   *
   * Note: it is already used inside [UastLanguagePlugin] conversion methods implementations
   * for Java, Kotlin and Scala.
   *
   * @return types of possible source PSI elements, which instances in principle
   *         can be converted to at least one of the specified [uastTypes]
   *         (or to [UElement] if no type was specified)
   */
  fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> {
    logger<UastLanguagePlugin>().warn(Exception("fallback to the PsiElement for ${this.javaClass}, it can have a performance impact"))
    return classSetOf(PsiElement::class.java)
  }
}

inline fun <reified T : UElement> UastLanguagePlugin.convertOpt(element: PsiElement?, parent: UElement?): T? {
  if (element == null) return null
  return convertElement(element, parent, T::class.java) as? T
}

@Deprecated("will throw exception if conversion fails", ReplaceWith("convertOpt"))
inline fun <reified T : UElement> UastLanguagePlugin.convert(element: PsiElement, parent: UElement?): T {
  return convertElement(element, parent, T::class.java) as T
}

inline fun <reified T : UElement> UastLanguagePlugin.convertWithParent(element: PsiElement?): T? {
  if (element == null) return null
  return convertElementWithParent(element, T::class.java) as? T
}