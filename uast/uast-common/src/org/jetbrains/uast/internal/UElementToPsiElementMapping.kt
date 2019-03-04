// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.internal

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement
import java.util.concurrent.ConcurrentHashMap

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

class ClassSet(vararg val initialClasses: Class<*>) {

  private val isSimple = initialClasses.size <= SIMPLE_CLASS_SET_LIMIT

  private lateinit var internalMapping: ConcurrentHashMap<Class<*>, Boolean>

  init {
    if (!isSimple)
      internalMapping = ConcurrentHashMap<Class<*>, Boolean>().apply {
        for (initialClass in initialClasses) {
          this[initialClass] = true
        }
      }
  }


  fun contains(cls: Class<*>): Boolean =
    if (isSimple) anyAssignable(cls) else internalMapping[cls] ?: anyAssignable(cls).also { internalMapping[cls] = it }

  private fun anyAssignable(cls: Class<*>): Boolean = initialClasses.any { it.isAssignableFrom(cls) }

}