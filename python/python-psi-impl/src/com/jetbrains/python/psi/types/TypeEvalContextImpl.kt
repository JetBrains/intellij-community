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
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider
import com.jetbrains.python.pyi.PyiLanguageDialect
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentMap
import kotlin.concurrent.Volatile

@ApiStatus.Internal
open class TypeEvalContextImpl internal constructor(
  /**
   * @return context constraints (see [TypeEvalConstraints]
   */
  @get:ApiStatus.Internal val constraints: TypeEvalConstraints,
) : TypeEvalContext() {


  private var myTrace: MutableList<String>? = null
  private var myTraceIndent = ""

  private val myProcessingContext = ThreadLocal.withInitial { ProcessingContext() }

  private var typeEngine: PyTypeEngine? = null
  protected val myEvaluated: MutableMap<PyTypedElement?, PyType?> = getConcurrentMapForCaching()
  protected val myEvaluatedReturn: MutableMap<PyCallable?, PyType?> = getConcurrentMapForCaching()
  protected val contextTypeCache: ConcurrentMap<Pair<PyExpression?, Any?>, PyType> = getConcurrentMapForCaching()

  internal constructor(allowDataFlow: Boolean, allowStubToAST: Boolean, allowCallContext: Boolean, origin: PsiFile?) : this(
    TypeEvalConstraints(allowDataFlow, allowStubToAST, allowCallContext, origin)
  )

  init {
    val origin = constraints.myOrigin
    if (origin != null) {
      typeEngine = PyTypeEngineProvider.createTypeResolver(origin.project)
    }
  }

  override fun toString(): String {
    return "TypeEvalContext(${constraints.myAllowDataFlow}, ${constraints.myAllowStubToAST}, ${constraints.myOrigin})"
  }

  override fun allowDataFlow(element: PsiElement): Boolean {
    return constraints.myAllowDataFlow && !element.inPyiFile() || inOrigin(element)
  }

  override fun allowReturnTypes(element: PsiElement): Boolean {
    return constraints.myAllowDataFlow && !element.inPyiFile() || inOrigin(element)
  }

  override fun allowCallContext(element: PsiElement): Boolean {
    return constraints.myAllowCallContext && !element.inPyiFile() && inOrigin(element)
  }

  override fun maySwitchToAST(element: PsiElement): Boolean {
    return constraints.myAllowStubToAST && !element.inPyiFile() || inOrigin(element)
  }

  override fun withTracing(): TypeEvalContext {
    if (myTrace == null) {
      myTrace = ArrayList()
    }
    return this
  }

  override fun trace(message: String, vararg args: Any?) {
    if (myTrace != null) {
      myTrace!!.add(myTraceIndent + String.format(message, *args))
    }
  }

  override fun traceIndent() {
    if (myTrace != null) {
      myTraceIndent += "  "
    }
  }

  override fun traceUnindent() {
    if (myTrace != null && myTraceIndent.length >= 2) {
      myTraceIndent = myTraceIndent.substring(0, myTraceIndent.length - 2)
    }
  }

  override fun printTrace(): String {
    return myTrace!!.joinToString("\n")
  }

  override fun tracing(): Boolean {
    return myTrace != null
  }

  @ApiStatus.Internal
  override fun <R> assumeType(element: PyTypedElement, type: PyType?, func: (TypeEvalContext?) -> R): R? {
    if (!Registry.Companion.`is`("python.use.better.control.flow.type.inference")) {
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
  override fun hasAssumptions(): Boolean {
    return this is AssumptionContext
  }

  @ApiStatus.Internal
  override fun isKnown(element: PyTypedElement): Boolean {
    return getKnownType(element) != null
  }

  override fun getKnownType(element: PyTypedElement): PyType? {
    if (element is PyInstantTypeProvider) {
      return element.getType(this, getKey())
    }
    return myEvaluated[element]?.also {
      assertValid(it, element)
    }
  }

  override fun getKnownReturnType(callable: PyCallable): PyType? {
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
    return Registry.Companion.`is`("python.use.separated.libraries.type.cache") && element.isLibraryElement()
  }

  override fun getType(element: PyTypedElement): PyType? {
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
      if (typeEngine != null && typeEngine!!.isSupportedForResolve(element)) {
        val startTime = System.currentTimeMillis()
        type = Ref.deref(typeEngine!!.resolveType(element, this is LibraryTypeEvalContext))
        val duration = System.currentTimeMillis() - startTime
        PyTypeEvaluationStatisticsService.getInstance().logHybridTypeEngineTime(duration)
      }
      else {
        val startTime = System.currentTimeMillis()
        type = element.getType(this, getKey())
        val duration = System.currentTimeMillis() - startTime
        PyTypeEvaluationStatisticsService.getInstance().logJBTypeEngineTime(duration)
      }

      assertValid(type, element)
      myEvaluated[element] = type ?: PyNullType
      type
    }
  }


  override fun getReturnType(callable: PyCallable): PyType? {
    if (canDelegateToLibraryContext(callable)) {
      val context = getLibraryContext(callable.project)
      return context.getReturnType(callable)
    }

    val knownReturnType = getKnownReturnType(callable)
    if (knownReturnType != null) {
      return if (knownReturnType is PyNullType) null else knownReturnType
    }
    return RecursionManager.doPreventingRecursion(callable to this, false) {
      val type = callable.getReturnType(this, getKey())
      assertValid(type, callable)
      myEvaluatedReturn[callable] = type ?: PyNullType
      type
    }
  }

  @get:ApiStatus.Experimental
  override val processingContext: ProcessingContext
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

  override val origin: PsiFile? = constraints.myOrigin

  override val usesExternalTypeProvider: Boolean
    get() = typeEngine != null

  @ApiStatus.Internal
  override fun getContextTypeCache(): MutableMap<Pair<PyExpression?, Any?>, PyType?> {
    return contextTypeCache
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val context = other as TypeEvalContextImpl

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

  class AssumptionContext(val myParent: TypeEvalContextImpl, element: PyTypedElement, type: PyType?) :
    TypeEvalContextImpl(myParent.constraints) {
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

  private class LibraryTypeEvalContext(constraints: TypeEvalConstraints) : TypeEvalContextImpl(constraints) {
    override fun canDelegateToLibraryContext(element: PyTypedElement): Boolean {
      // It's already the library-context.
      return false
    }
  }

  class OptimizedTypeEvalContext(allowDataFlow: Boolean, allowStubToAST: Boolean, allowCallContext: Boolean, origin: PsiFile?) :
    TypeEvalContextImpl(allowDataFlow, allowStubToAST, allowCallContext, origin) {
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

    fun getFallbackContext(project: Project): TypeEvalContext? {
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
      if (Registry.Companion.`is`("python.typing.weak.keys.type.eval.context")) {
        return CollectionFactory.createConcurrentWeakKeySoftValueMap(
          10,
          0.75f,
          concurrencyLevel,
          HashingStrategy.canonical<T>()
        )
      }
      else if (Registry.Companion.`is`("python.typing.soft.keys.type.eval.context")) {
        return CollectionFactory.createConcurrentSoftKeySoftValueMap(10, 0.75f, concurrencyLevel)
      }
      else {
        return CollectionFactory.createConcurrentSoftValueMap()
      }
    }

    protected val logger: Logger = logger<TypeEvalContextImpl>()


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