// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.internal

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement
import java.util.concurrent.ConcurrentHashMap

class UElementToPsiElementMapping(val baseMapping: Map<Class<out UElement>, ClassSet>) {

  constructor(vararg mapping: Pair<Class<out UElement>, ClassSet>) : this(mapOf(*mapping))

  private val internalMapping = ConcurrentHashMap<Class<out UElement>, List<ClassSet>>()

  private operator fun get(uCls: Class<out UElement>): List<ClassSet> {
    internalMapping[uCls]?.let { return it }

    var result = mutableListOf<ClassSet>()

    for ((key, set) in baseMapping.entries) {
      if (uCls.isAssignableFrom(key)) result.add(set)
    }

    if (result.size > MERGING_CLASS_SET_LIMIT) {
      result = mutableListOf(ClassSet(*result.flatMap { it.initialClasses.toList() }.toTypedArray()))
    }

    internalMapping[uCls] = result
    return result
  }

  fun canConvert(psiCls: Class<out PsiElement>, targets: List<Class<out UElement>>): Boolean {
    for (target in targets) {
      if (this[target].any { it.contains(psiCls) })
        return true
    }
    return false
  }


}

private const val MERGING_CLASS_SET_LIMIT = 5

class ClassSet(vararg val initialClasses: Class<*>) {

  private val internalMapping = ConcurrentHashMap<Class<*>, Boolean>().apply {
    for (initialClass in initialClasses) {
      this[initialClass] = true
    }
  }

  fun contains(cls: Class<*>): Boolean =
    internalMapping[cls] ?: initialClasses.any { it.isAssignableFrom(cls) }.also { internalMapping[cls] = it }

}