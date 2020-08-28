// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.internal

import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.uast.UElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class UElementToPsiElementMapping(val baseMapping: Map<Class<out UElement>, ClassSet>) {

  constructor(vararg mapping: Pair<Class<out UElement>, ClassSet>) : this(mapOf(*mapping))

  private val internalMapping = ConcurrentHashMap<Class<out UElement>, ClassSet>()

  operator fun get(uCls: Class<out UElement>): ClassSet {
    internalMapping[uCls]?.let { return it }

    val applicableClassSets = mutableListOf<ClassSet>()

    for ((key, set) in baseMapping.entries) {
      if (uCls.isAssignableFrom(key)) applicableClassSets.add(set)
    }

    val result = mergeClassSets(applicableClassSets)
    internalMapping[uCls] = result
    return result
  }


  fun canConvert(psiCls: Class<out PsiElement>, targets: Array<out Class<out UElement>>): Boolean {
    for (target in targets) {
      if (this[target].contains(psiCls))
        return true
    }
    return false
  }


}

private const val SIMPLE_CLASS_SET_LIMIT = 5

private fun mergeClassSets(result: List<ClassSet>) = result.singleOrNull() ?: ClassSet(
  *result.flatMap { it.initialClasses.asIterable() }.toTypedArray())


class ClassSet(vararg val initialClasses: Class<out PsiElement>) : Set<Class<out PsiElement>> {

  private val isSimple = initialClasses.size <= SIMPLE_CLASS_SET_LIMIT

  private lateinit var internalMapping: ConcurrentMap<Class<out PsiElement>, Boolean>

  init {
    if (!isSimple)
      internalMapping = ContainerUtil.createConcurrentSoftMap<Class<out PsiElement>, Boolean>().apply {
        for (initialClass in initialClasses) {
          this[initialClass] = true
        }
      }
  }

  override val size: Int get() = initialClasses.size
  override fun isEmpty(): Boolean = initialClasses.isEmpty()
  override fun iterator(): Iterator<Class<out PsiElement>> = initialClasses.iterator()
  override fun containsAll(elements: Collection<Class<out PsiElement>>): Boolean = elements.all { contains(it) }

  override fun contains(element: Class<out PsiElement>): Boolean =
    if (isSimple)
      initialClasses.any { it.isAssignableFrom(element) }
    else
      internalMapping[element]
      ?: initialClasses.any { it.isAssignableFrom(element) }.also { internalMapping[element] = it }
}


class SetListWrapper<out T>(vararg val sets: Set<T>) : Set<T> {
  override val size: Int = sets.sumBy { it.size }
  override fun contains(element: @UnsafeVariance T): Boolean = sets.any { it.contains(element) }
  override fun containsAll(elements: Collection<@UnsafeVariance T>): Boolean = elements.all { contains(it) }
  override fun isEmpty(): Boolean = size == 0
  override fun iterator(): Iterator<T> = sets.asSequence().flatMap { it.iterator().asSequence() }.iterator()
}