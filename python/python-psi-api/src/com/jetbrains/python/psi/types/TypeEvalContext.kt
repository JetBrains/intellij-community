// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyInstantTypeProvider
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.external.ExternalPyTypeResolver
import com.jetbrains.python.psi.types.external.ExternalPyTypeResolverProvider.Companion.createTypeResolver
import com.jetbrains.python.pyi.PyiLanguageDialect
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentMap
import kotlin.concurrent.Volatile

sealed class TypeEvalContext(
  /**
   * @return context constraints (see [TypeEvalConstraints]
   */
  @get:ApiStatus.Internal val constraints: TypeEvalConstraints,
) {
  /**
   * This class ensures that only [TypeEvalContext] instances can directly invoke
   * [PyTypedElement.getType] and everybody else has to
   * access its result though [.getType] or [.getReturnType].
   * Hence, the inferred type information cannot bypass caching in [TypeEvalContext].
   */
  sealed class Key

  private object KeyImpl : Key()

  private var myTrace: MutableList<String>? = null
  private var myTraceIndent = ""

  private val myProcessingContext = ThreadLocal.withInitial { ProcessingContext() }

  private var externalTypeResolver: ExternalPyTypeResolver? = null
  protected val myEvaluated: MutableMap<PyTypedElement?, PyType?> = getConcurrentMapForCaching()
  protected val myEvaluatedReturn: MutableMap<PyCallable?, PyType?> = getConcurrentMapForCaching()
  protected val contextTypeCache: ConcurrentMap<Pair<PyExpression?, Any?>, PyType> = getConcurrentMapForCaching()

  private constructor(allowDataFlow: Boolean, allowStubToAST: Boolean, allowCallContext: Boolean, origin: PsiFile?) : this(
    TypeEvalConstraints(allowDataFlow, allowStubToAST, allowCallContext, origin)
  )

  init {
    if (constraints.myOrigin != null) {
      externalTypeResolver = createTypeResolver(constraints.myOrigin.project)
    }
  }

  override fun toString(): String {
    return "TypeEvalContext(${constraints.myAllowDataFlow}, ${constraints.myAllowStubToAST}, ${constraints.myOrigin})"
  }

  fun allowDataFlow(element: PsiElement): Boolean {
    return constraints.myAllowDataFlow && !element.inPyiFile() || inOrigin(element)
  }

  fun allowReturnTypes(element: PsiElement): Boolean {
    return constraints.myAllowDataFlow && !element.inPyiFile() || inOrigin(element)
  }

  fun allowCallContext(element: PsiElement): Boolean {
    return constraints.myAllowCallContext && !element.inPyiFile() && inOrigin(element)
  }

  fun maySwitchToAST(element: PsiElement): Boolean {
    return constraints.myAllowStubToAST && !element.inPyiFile() || inOrigin(element)
  }

  fun withTracing(): TypeEvalContext {
    if (myTrace == null) {
      myTrace = ArrayList()
    }
    return this
  }

  open fun trace(message: String, vararg args: Any?) {
    if (myTrace != null) {
      myTrace!!.add(myTraceIndent + String.format(message, *args))
    }
  }

  open fun traceIndent() {
    if (myTrace != null) {
      myTraceIndent += "  "
    }
  }

  open fun traceUnindent() {
    if (myTrace != null && myTraceIndent.length >= 2) {
      myTraceIndent = myTraceIndent.substring(0, myTraceIndent.length - 2)
    }
  }

  fun printTrace(): String {
    return myTrace!!.joinToString("\n")
  }

  fun tracing(): Boolean {
    return myTrace != null
  }

  @ApiStatus.Internal
  fun <R> assumeType(element: PyTypedElement, type: PyType?, func: (TypeEvalContext?) -> R): R? {
    if (!Registry.`is`("python.use.better.control.flow.type.inference")) {
      return func(this)
    }
    if (getKnownType(element) != null) {
      // Temporary solution, as overwriting known type might introduce inconsistencies with its dependencies.
      return null
    }
    val context = AssumptionContext(this, element, type)
    return try {
      func(context)
    }
    finally {
      element.manager.dropResolveCaches()
    }
  }

  @ApiStatus.Internal
  fun hasAssumptions(): Boolean {
    return this is AssumptionContext
  }

  @ApiStatus.Internal
  fun isKnown(element: PyTypedElement): Boolean {
    return getKnownType(element) != null
  }

  protected open fun getKnownType(element: PyTypedElement): PyType? {
    if (element is PyInstantTypeProvider) {
      return element.getType(this, KeyImpl)
    }
    return myEvaluated[element]?.also {
      assertValid(it, element)
    }
  }

  protected open fun getKnownReturnType(callable: PyCallable): PyType? {
    return myEvaluatedReturn[callable]?.also {
      assertValid(it, callable)
    }
  }

  private fun getLibraryContext(project: Project): TypeEvalContext {
    // code completion will always have a new PsiFile, use the original file instead
    val origin = constraints.myOrigin?.originalFile
    val constraints = TypeEvalConstraints(
      constraints.myAllowDataFlow,
      constraints.myAllowStubToAST,
      constraints.myAllowCallContext,
      origin,
    )
    return project.service<TypeEvalContextCache>()
      .getLibraryContext(LibraryTypeEvalContext(constraints))
  }

  /**
   * If true the element's type will be calculated and stored in the long-life context bounded to the PyLibraryModificationTracker.
   */
  protected open fun canDelegateToLibraryContext(element: PyTypedElement): Boolean {
    return Registry.`is`("python.use.separated.libraries.type.cache") && element.isLibraryElement()
  }

  open fun getType(element: PyTypedElement): PyType? {
    if (canDelegateToLibraryContext(element)) {
      val context = getLibraryContext(element.project)
      return context.getType(element)
    }

    val knownType = getKnownType(element)
    if (knownType != null) {
      return if (knownType === PyNullType) null else knownType
    }

    return RecursionManager.doPreventingRecursion(element to this, false) {
      val type: PyType?
      if (externalTypeResolver != null && externalTypeResolver!!.isSupportedForResolve(element)) {
        type = Ref.deref(externalTypeResolver!!.resolveType(element, this is LibraryTypeEvalContext))
      }
      else {
        type = element.getType(this, KeyImpl)
      }

      assertValid(type, element)
      myEvaluated[element] = type ?: PyNullType
      type
    }
  }


  open fun getReturnType(callable: PyCallable): PyType? {
    if (canDelegateToLibraryContext(callable)) {
      val context = getLibraryContext(callable.project)
      return context.getReturnType(callable)
    }

    val knownReturnType = getKnownReturnType(callable)
    if (knownReturnType != null) {
      return if (knownReturnType is PyNullType) null else knownReturnType
    }
    return RecursionManager.doPreventingRecursion(callable to this, false) {
      val type = callable.getReturnType(this, KeyImpl)
      assertValid(type, callable)
      myEvaluatedReturn[callable] = type ?: PyNullType
      type
    }
  }

  @get:ApiStatus.Experimental
  val processingContext: ProcessingContext
    /**
     * Normally, each [PyTypeProvider] is supposed to perform all the necessary analysis independently
     * and hence should completely isolate its state. However, on rare occasions when several type providers have to
     * recursively call each other, it might be necessary to preserve some state for subsequent calls to the same provider with
     * the same instance of [TypeEvalContext]. Each [TypeEvalContext] instance contains an associated thread-local
     * [ProcessingContext] that can be used for such caching. Should be used with discretion.
     *
     * @return a thread-local instance of [ProcessingContext] bound to this [TypeEvalContext] instance
     */
    get() = myProcessingContext.get()

  val origin: PsiFile? = constraints.myOrigin

  val usesExternalTypeProvider: Boolean
    get() = externalTypeResolver != null

  @ApiStatus.Internal
  fun getContextTypeCache(): MutableMap<Pair<PyExpression?, Any?>, PyType?> {
    return contextTypeCache
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val context = other as TypeEvalContext

    return this.constraints == context.constraints
  }

  override fun hashCode(): Int {
    return constraints.hashCode()
  }

  private fun inOrigin(element: PsiElement): Boolean {
    return isSameVirtualFile(constraints.myOrigin, element.containingFile) ||
           isSameVirtualFile(constraints.myOrigin, element.getContextFile())
  }

  private object PyNullType : PyType {
    override fun resolveMember(
      name: String,
      location: PyExpression?,
      direction: AccessDirection,
      resolveContext: PyResolveContext,
    ): List<RatedResolveResult> {
      return emptyList()
    }

    override fun getCompletionVariants(
      completionPrefix: String?,
      location: PsiElement,
      context: ProcessingContext,
    ): Array<Any> {
      return ArrayUtil.EMPTY_OBJECT_ARRAY
    }

    override val name: String = "null"

    override val isBuiltin: Boolean = false

    override fun assertValid(message: String?) {
    }
  }

  private class TypeEvalContextImpl(allowDataFlow: Boolean, allowStubToAST: Boolean, allowCallContext: Boolean, origin: PsiFile?) :
    TypeEvalContext(allowDataFlow, allowStubToAST, allowCallContext, origin)

  private class AssumptionContext(val myParent: TypeEvalContext, element: PyTypedElement, type: PyType?) :
    TypeEvalContext(myParent.constraints) {
    init {
      myEvaluated[element] = type ?: PyNullType
    }

    override fun getKnownType(element: PyTypedElement): PyType? {
      return super.getKnownType(element) ?: myParent.getKnownType(element)
    }

    override fun getKnownReturnType(callable: PyCallable): PyType? {
      return super.getKnownReturnType(callable) ?: myParent.getKnownReturnType(callable)
    }

    override fun trace(message: String, vararg args: Any?) {
      myParent.trace(message, *args)
    }

    override fun traceIndent() {
      myParent.traceIndent()
    }

    override fun traceUnindent() {
      myParent.traceUnindent()
    }

    override fun equals(other: Any?): Boolean {
      // Otherwise, it can be equal to other AssumptionContext with same constraints
      return this === other
    }
  }

  private class LibraryTypeEvalContext(constraints: TypeEvalConstraints) : TypeEvalContext(constraints) {
    override fun canDelegateToLibraryContext(element: PyTypedElement): Boolean {
      // It's already the library-context.
      return false
    }
  }

  private class OptimizedTypeEvalContext(allowDataFlow: Boolean, allowStubToAST: Boolean, allowCallContext: Boolean, origin: PsiFile?) :
    TypeEvalContext(allowDataFlow, allowStubToAST, allowCallContext, origin) {
    @Volatile
    private var codeInsightFallback: TypeEvalContext? = null

    fun shouldSwitchToFallbackContext(element: PsiElement): Boolean {
      var file = element.containingFile
      if (file is PyExpressionCodeFragment) {
        val context = file.context
        if (context != null) {
          file = context.containingFile
        }
      }
      val constraints = this.constraints
      return constraints.myOrigin != null && !isSameVirtualFile(
        file,
        constraints.myOrigin
      ) && (file is PyFile) && !constraints.myAllowDataFlow && !constraints.myAllowStubToAST && !constraints.myAllowCallContext
    }

    fun getFallbackContext(project: Project?): TypeEvalContext? {
      if (codeInsightFallback == null) {
        codeInsightFallback = codeInsightFallback(project)
      }
      return codeInsightFallback
    }

    override fun getKnownType(element: PyTypedElement): PyType? {
      if (shouldSwitchToFallbackContext(element)) {
        return getFallbackContext(element.project)!!.getKnownType(element)
      }
      return super.getKnownType(element)
    }

    override fun getKnownReturnType(callable: PyCallable): PyType? {
      if (shouldSwitchToFallbackContext(callable)) {
        return getFallbackContext(callable.project)!!.getKnownReturnType(callable)
      }
      return super.getKnownReturnType(callable)
    }

    override fun getType(element: PyTypedElement): PyType? {
      if (shouldSwitchToFallbackContext(element)) {
        return getFallbackContext(element.project)!!.getType(element)
      }
      return super.getType(element)
    }

    override fun getReturnType(callable: PyCallable): PyType? {
      if (shouldSwitchToFallbackContext(callable)) {
        return getFallbackContext(callable.project)!!.getReturnType(callable)
      }
      return super.getReturnType(callable)
    }
  }

  @ApiStatus.Internal
  companion object {
    private fun <T> getConcurrentMapForCaching(): ConcurrentMap<T & Any, PyType> {
      // In the current implementation, this value is only used to initialize the map and is basically ignored
      // Just in case, set it to a reasonable value
      // `Runtime.availableProcessors` shouldn't be called here, as that is a potentially expensive operation
      val concurrencyLevel = 4
      if (Registry.`is`("python.typing.weak.keys.type.eval.context")) {
        return CollectionFactory.createConcurrentWeakKeySoftValueMap(
          10,
          0.75f,
          concurrencyLevel,
          HashingStrategy.canonical<T>()
        )
      }
      else if (Registry.`is`("python.typing.soft.keys.type.eval.context")) {
        return CollectionFactory.createConcurrentSoftKeySoftValueMap(10, 0.75f, concurrencyLevel)
      }
      else {
        return CollectionFactory.createConcurrentSoftValueMap()
      }
    }

    protected val logger: Logger = logger<TypeEvalContext>()

    /**
     * Create a context for code completion.
     *
     *
     * It is as detailed as [TypeEvalContext.userInitiated], but allows inferring types based on the context in which
     * the analyzed code was called or may be called. Since this is basically guesswork, the results should be used only for code completion.
     */
    @JvmStatic
    fun codeCompletion(project: Project, origin: PsiFile?): TypeEvalContext {
      return getContextFromCache(project, TypeEvalContextImpl(true, true, true, origin))
    }

    /**
     * Create the most detailed type evaluation context for user-initiated actions.
     *
     *
     * Should be used go to definition, find usages, refactorings, documentation.
     *
     *
     * For code completion see [TypeEvalContext.codeCompletion].
     */
    @JvmStatic
    fun userInitiated(project: Project, origin: PsiFile?): TypeEvalContext {
      return getContextFromCache(project, TypeEvalContextImpl(true, true, false, origin))
    }

    /**
     * Create a type evaluation context for performing analysis operations on the specified file which is currently open in the editor,
     * without accessing stubs. For such a file, additional slow operations are allowed.
     *
     *
     * Inspections should not create a new type evaluation context. They should re-use the context of the inspection session.
     */
    @JvmStatic
    fun codeAnalysis(project: Project, origin: PsiFile?): TypeEvalContext {
      return getContextFromCache(project, buildCodeAnalysisContext(origin))
    }

    /**
     * Create the most shallow type evaluation context for code insight purposes when other more detailed contexts are not available.
     * It's use should be minimized.
     *
     * @param project pass project here to enable cache. Pass null if you do not have any project.
     * **Always** do your best to pass project here: it increases performance!
     */
    @JvmStatic
    fun codeInsightFallback(project: Project?): TypeEvalContext {
      val anchor = TypeEvalContextImpl(false, false, false, null)
      if (project != null) {
        return getContextFromCache(project, anchor)
      }
      return anchor
    }

    /**
     * Create a type evaluation context for deeper and slower code insight.
     *
     *
     * Should be used only when normal code insight context is not enough for getting good results.
     */
    @JvmStatic
    fun deepCodeInsight(project: Project): TypeEvalContext {
      return getContextFromCache(project, TypeEvalContextImpl(false, true, false, null))
    }

    private fun buildCodeAnalysisContext(origin: PsiFile?): TypeEvalContext {
      if (Registry.`is`("python.optimized.type.eval.context")) {
        return OptimizedTypeEvalContext(false, false, false, origin)
      }
      return TypeEvalContextImpl(false, false, false, origin)
    }

    /**
     * Moves context through cache returning one from cache (if exists).
     *
     * @param project current project
     * @param context context to fetch from cache
     * @return context to use
     * @see TypeEvalContextCache.getContext
     */
    private fun getContextFromCache(project: Project, context: TypeEvalContext): TypeEvalContext {
      return project.service<TypeEvalContextCache>().getContext(context)
    }

    private fun PsiElement.isLibraryElement(): Boolean {
      val vFile = this.containingFile?.originalFile?.virtualFile
      return vFile != null && ("pyi" == vFile.extension || ProjectFileIndex.getInstance(this.project).isInLibrary(vFile))
    }

    private fun assertValid(result: PyType?, element: PyTypedElement) {
      result?.assertValid(element.toString())
    }

    private fun isSameVirtualFile(file1: PsiFile?, file2: PsiFile?): Boolean {
      if (file1 == null) return false
      if (file2 == null) return false
      return file1.viewProvider.virtualFile == file2.viewProvider.virtualFile
    }

    private fun PsiElement.inPyiFile(): Boolean {
      val containingFile = this.containingFile ?: return false
      if (containingFile.isPyiFile) {
        return true
      }
      val contextFile: PsiFile? = getContextFile()
      return contextFile != null && contextFile.isPyiFile
    }

    private fun PsiElement.getContextFile(): PsiFile? {
      val file = this.containingFile ?: return null
      val context = file.context ?: return file
      return context.getContextFile()
    }

    private val PsiFile.isPyiFile: Boolean
      get() = this.language == PyiLanguageDialect.getInstance()
  }
}