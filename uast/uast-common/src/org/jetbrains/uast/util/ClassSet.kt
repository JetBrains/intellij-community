// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.util

import com.intellij.util.SofterReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


interface ClassSet<out T> {
  fun isEmpty(): Boolean
  operator fun contains(element: Class<out @UnsafeVariance T>): Boolean
}

class ClassSetImpl<out T>(vararg val initialClasses: Class<out T>) : ClassSet<T> {

  private val isSimple = initialClasses.size <= SIMPLE_CLASS_SET_LIMIT

  private lateinit var _internalMapping: SofterReference<ConcurrentMap<Class<out T>, Boolean>>
  private inline val internalMapping: ConcurrentMap<Class<out T>, Boolean>
    get() = _internalMapping.get() ?: initMappings()

  init {
    if (!isSimple) initMappings()
  }

  private fun initMappings() =
    ConcurrentHashMap<Class<out T>, Boolean>().apply {
      for (initialClass in initialClasses)
        this[initialClass] = true
      _internalMapping = SofterReference(this)
    }

  override fun isEmpty(): Boolean = initialClasses.isEmpty()

  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean =
    if (isSimple)
      initialClasses.any { it.isAssignableFrom(element) }
    else
      internalMapping[element]
      ?: initialClasses.any { it.isAssignableFrom(element) }.also { internalMapping[element] = it }
}

inline class ClassSetsWrapper<out T>(val sets: Array<ClassSet<@UnsafeVariance T>>) : ClassSet<T> {
  override fun isEmpty(): Boolean = sets.all { it.isEmpty() }
  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean = sets.any { it.contains(element) }
}

private const val SIMPLE_CLASS_SET_LIMIT = 5