package com.intellij.python.lsp.core.type

import com.google.common.util.concurrent.Striped
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.withLock

@ApiStatus.Internal
abstract class LspTypeEvalContext(val psiFile: PsiFile) {
  /**
   * Context should be available only for the same timestamp of the file.
   */
  private val psiFileTimestamp: Long = psiFile.modificationStamp

  private val stringTypeLocks = Striped.lock(64)
  private val requestedTypesCount = AtomicInteger(0)
  private val isFullLoaded: AtomicBoolean = AtomicBoolean(false)

  /** Every highlighting thread reads and writes this map, so it must be concurrent. */
  private val cacheElementToStringType: ConcurrentMap<PyTypedElement, String> = ContainerUtil.createConcurrentWeakMap()
  private val cacheStringTypeToType: ConcurrentSkipListMap<String, Ref<PyType?>> = ConcurrentSkipListMap()
  private val unresolvedSet: ConcurrentSkipListSet<String> = ConcurrentSkipListSet()

  protected abstract fun requestTypes(pyTypedElements: @Unmodifiable Collection<PyTypedElement>): List<String?>?

  open fun provideType(element: PyTypedElement, isUserInitiated: Boolean): Ref<PyType?>? {
    val stringType = cacheElementToStringType[element] ?: resolveTypeByLsp(element, isUserInitiated)
    if (stringType == null)
      return null

    return stringTypeLocks.get(stringType).withLock {
      if (stringType in unresolvedSet) {
        return@withLock null
      }
      val cachedResolved = cacheStringTypeToType[stringType]
      if (cachedResolved != null) {
        return@withLock cachedResolved
      }
      val resolvedType = resolveStringType(element, stringType)
      if (resolvedType != null) {
        cacheStringTypeToType[stringType] = resolvedType
      }
      else {
        unresolvedSet.add(stringType)
      }

      return@withLock resolvedType
    }
  }

  protected abstract fun resolveStringType(element: PyTypedElement, stringType: String): Ref<PyType?>?

  private fun resolveTypeByLsp(element: PyTypedElement, isUserInitiated: Boolean): String? =
    if (!isUserInitiated && requestedTypesCount.incrementAndGet() >= FULL_LOAD_THRESHOLD && !ApplicationManager.getApplication().isDispatchThread) {
      loadAllTypes(element)
      cacheElementToStringType[element]
    }
    else {
      synchronized(element) {
        loadSingleType(element)
      }
    }

  private fun loadAllTypes(element: PyTypedElement) {
    if (isFullLoaded.get())
      return
    synchronized(this) {
      if (isFullLoaded.get())
        return
      val requestedTypes = collectElementsForCalculation(element) - cacheElementToStringType.keys
      if (requestedTypes.isEmpty()) return

      val contents = requestTypes(requestedTypes)
      if (contents == null) {
        thisLogger().warn("Failed to load all types for ${psiFile.name} Current stamp ${psiFile.modificationStamp} calculating: ${psiFileTimestamp}")
        return
      }

      if (contents.size != requestedTypes.size) {
        thisLogger().warn("Requested ${requestedTypes.size} types for ${psiFile.name} but got ${contents.size}")
        return
      }

      // The map refuses a null value. An element without a type stays absent, as it was before.
      for ((requested, content) in requestedTypes.zip(contents)) {
        if (content != null) {
          cacheElementToStringType[requested] = content
        }
      }
      isFullLoaded.set(true)
    }
  }

  private fun loadSingleType(typedElement: PyTypedElement): String? {
    cacheElementToStringType[typedElement]?.let { return it }
    val types = requestTypes(listOf(typedElement))
    if (types.isNullOrEmpty()) return null
    val resolvedType = types.single() ?: ""
    cacheElementToStringType[typedElement] = resolvedType
    return resolvedType
  }

  companion object {
    private const val FULL_LOAD_THRESHOLD: Int = 10

    fun collectElementsForCalculation(element: PsiElement): List<PyTypedElement> {
      val psiFile = element.containingFile ?: return emptyList()
      val visitor = LspCollectSupportedTypesVisitor()
      psiFile.acceptChildren(visitor)
      return visitor.result
    }
  }
}
