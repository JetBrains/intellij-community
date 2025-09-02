// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.Contract
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.ClassSetsWrapper
import org.jetbrains.uast.util.emptyClassSet
import java.util.*

@Service(Service.Level.PROJECT)
@Deprecated("use UastFacade or UastLanguagePlugin instead", ReplaceWith("UastFacade"))
class UastContext(val project: Project) : UastLanguagePlugin by UastFacade {
  fun findPlugin(element: PsiElement): UastLanguagePlugin? = UastFacade.findPlugin(element)

  fun getMethod(method: PsiMethod): UMethod = convertWithParent(method)!!

  fun getVariable(variable: PsiVariable): UVariable = convertWithParent(variable)!!

  fun getClass(clazz: PsiClass): UClass = convertWithParent(clazz)!!
}

/**
 * The main entry point to uast-conversions.
 *
 * In the most cases you could use [toUElement] or [toUElementOfExpectedTypes] extension methods instead of using the `UastFacade` directly
 */
object UastFacade : UastLanguagePlugin {

  override val language: Language = object : Language("UastContextLanguage") {}

  override val priority: Int
    get() = 0

  val languagePlugins: Collection<UastLanguagePlugin>
    get() = UastLanguagePlugin.getInstances()
  private var cachedLastPlugin: UastLanguagePlugin = this

  fun findPlugin(element: PsiElement): UastLanguagePlugin? = findPlugin(element.language)
  fun findPlugin(language: Language): UastLanguagePlugin? {
    val cached = cachedLastPlugin
    if (language === cached.language) return cached
    val plugin = languagePlugins.firstOrNull { it.language === language }
    if (plugin != null) cachedLastPlugin = plugin
    return plugin
  }

  override fun isFileSupported(fileName: String): Boolean = languagePlugins.any { it.isFileSupported(fileName) }

  override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
    return findPlugin(element)?.convertElement(element, parent, requiredType)
  }

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
    if (element is PsiWhiteSpace) {
      return null
    }
    return findPlugin(element)?.convertElementWithParent(element, requiredType)
  }

  override fun getMethodCallExpression(
    element: PsiElement,
    containingClassFqName: String?,
    methodName: String
  ): UastLanguagePlugin.ResolvedMethod? {
    return findPlugin(element)?.getMethodCallExpression(element, containingClassFqName, methodName)
  }

  override fun getConstructorCallExpression(
    element: PsiElement,
    fqName: String
  ): UastLanguagePlugin.ResolvedConstructor? {
    return findPlugin(element)?.getConstructorCallExpression(element, fqName)
  }

  override fun isExpressionValueUsed(element: UExpression): Boolean {
    val language = element.getLanguage()
    return findPlugin(language)?.isExpressionValueUsed(element) ?: false
  }

  private tailrec fun UElement.getLanguage(): Language {
    sourcePsi?.language?.let { return it }
    val containingElement = this.uastParent ?: throw IllegalStateException("At least UFile should have a language")
    return containingElement.getLanguage()
  }

  override fun <T : UElement> convertElementWithParent(element: PsiElement, requiredTypes: Array<out Class<out T>>): T? =
    findPlugin(element)?.convertElementWithParent(element, requiredTypes)

  override fun <T : UElement> convertToAlternatives(element: PsiElement, requiredTypes: Array<out Class<out T>>): Sequence<T> =
    findPlugin(element)?.convertToAlternatives(element, requiredTypes) ?: emptySequence()

  private interface UastPluginListener {
    fun onPluginsChanged()
  }

  private val exposedListeners = Collections.newSetFromMap(CollectionFactory.createConcurrentWeakIdentityMap<UastPluginListener, Boolean>())

  init {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, object: DynamicPluginListener {
      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // avoid Language mem-leak on its plugin unload
        clearCachedPlugin()
      }
    })
    UastLanguagePlugin.EP.addChangeListener({ exposedListeners.forEach(UastPluginListener::onPluginsChanged) }, null)
  }

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
    object : ClassSet<PsiElement>, UastPluginListener {

      fun initInner(): ClassSetsWrapper<PsiElement> = ClassSetsWrapper(languagePlugins.map2Array { it.getPossiblePsiSourceTypes(*uastTypes) })

      private var inner: ClassSetsWrapper<PsiElement> = initInner()

      override fun onPluginsChanged() {
        inner = initInner()
      }

      override fun isEmpty(): Boolean = inner.isEmpty()

      override fun contains(element: Class<out PsiElement>): Boolean = inner.contains(element)

      override fun toList(): List<Class<out PsiElement>> = inner.toList()

    }.also { exposedListeners.add(it) }

  fun clearCachedPlugin() {
    cachedLastPlugin = this
  }
}


/**
 * Converts the element to UAST.
 */
fun PsiElement?.toUElement(): UElement? = if (this == null) null else UastFacade.convertElementWithParent(this, null)

/**
 * Converts the element to an UAST element of the given type. Returns null if the PSI element type does not correspond
 * to the given UAST element type.
 */
@Suppress("UNCHECKED_CAST")
@Contract("null, _ -> null")
fun <T : UElement> PsiElement?.toUElement(cls: Class<out T>): T? = if (this == null) null else UastFacade.convertElementWithParent(this, cls) as T?

@Suppress("UNCHECKED_CAST")
@SafeVarargs
fun <T : UElement> PsiElement?.toUElementOfExpectedTypes(vararg classes: Class<out T>): T? =
  this?.let {
    if (classes.isEmpty()) {
      UastFacade.convertElementWithParent(this, UElement::class.java) as T?
    }
    else if (classes.size == 1) {
      UastFacade.convertElementWithParent(this, classes[0]) as T?
    }
    else {
      UastFacade.convertElementWithParent(this, classes)
    }
  }


inline fun <reified T : UElement> PsiElement?.toUElementOfType(): T? = toUElement(T::class.java)

/**
 * Finds an UAST element of a given type at the given [offset] in the specified file. Returns null if there is no UAST
 * element of the given type at the given offset.
 */
fun <T : UElement> PsiFile.findUElementAt(offset: Int, cls: Class<out T>): T? {
  val element = findElementAt(offset) ?: return null
  val uElement = element.toUElement() ?: return null
  @Suppress("UNCHECKED_CAST")
  return uElement.withContainingElements.firstOrNull { cls.isInstance(it) } as T?
}

/**
 * Finds an UAST element of the given type among the parents of the given PSI element.
 */
@JvmOverloads
fun <T : UElement> PsiElement?.getUastParentOfType(cls: Class<out T>, strict: Boolean = false): T? = this?.run {
  val firstUElement = getFirstUElement(this, strict) ?: return null

  @Suppress("UNCHECKED_CAST")
  return firstUElement.withContainingElements.firstOrNull { cls.isInstance(it) } as T?
}

/**
 * Finds an UAST element of any given type among the parents of the given PSI element.
 */
@JvmOverloads
fun PsiElement?.getUastParentOfTypes(classes: Array<Class<out UElement>>, strict: Boolean = false): UElement? = this?.run {
  val firstUElement = getFirstUElement(this, strict) ?: return null

  return firstUElement.withContainingElements.firstOrNull { uElement ->
    classes.any { cls -> cls.isInstance(uElement) }
  }
}

inline fun <reified T : UElement> PsiElement?.getUastParentOfType(strict: Boolean = false): T? = getUastParentOfType(T::class.java, strict)

@JvmField
val DEFAULT_TYPES_LIST: Array<Class<out UElement>> = arrayOf(UElement::class.java)

@JvmField
val DEFAULT_EXPRESSION_TYPES_LIST: Array<Class<out UExpression>> = arrayOf(UExpression::class.java)

/**
 * @return types of possible source PSI elements of [language], which instances in principle
 *         can be converted to at least one of the specified [uastTypes]
 *         (or to [UElement] if no type was specified)
 *
 * @see UastFacade.getPossiblePsiSourceTypes
 */
fun getPossiblePsiSourceTypes(language: Language, vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
  UastFacade.findPlugin(language)?.getPossiblePsiSourceTypes(*uastTypes) ?: emptyClassSet()

fun getFirstUElement(psiElement: PsiElement, strict: Boolean = false): UElement? {
  val startingElement = if (strict) psiElement.parent else psiElement
  val parentSequence = generateSequence(startingElement, PsiElement::getParent)
  return parentSequence.mapNotNull { it.toUElement() }.firstOrNull()
}
