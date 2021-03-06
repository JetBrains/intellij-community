// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.util

import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.map2Array
import java.util.concurrent.ConcurrentMap


interface ClassSet<out T> {
  fun isEmpty(): Boolean
  operator fun contains(element: Class<out @UnsafeVariance T>): Boolean
  fun toList(): List<Class<out @UnsafeVariance T>>
}

fun <T> T?.isInstanceOf(classSet: ClassSet<T>): Boolean =
  this?.let { classSet.contains(it.javaClass) } ?: false

fun <T> ClassSet<T>.hasClassOf(instance: T?): Boolean =
  instance?.let { contains(it.javaClass) } ?: false

private class ClassSetImpl<out T>(vararg val initialClasses: Class<out T>) : ClassSet<T> {

  private val isSimple = initialClasses.size <= SIMPLE_CLASS_SET_LIMIT

  private lateinit var internalMapping: ConcurrentMap<Class<out T>, Boolean>

  init {
    if (!isSimple)
      internalMapping = CollectionFactory.createConcurrentWeakMap<Class<out T>, Boolean>().apply {
        for (initialClass in initialClasses)
          this[initialClass] = true
      }
  }

  override fun isEmpty(): Boolean = initialClasses.isEmpty()

  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean =
    if (isSimple)
      initialClasses.any { it.isAssignableFrom(element) }
    else
      internalMapping[element]
      ?: initialClasses.any { it.isAssignableFrom(element) }.also { internalMapping[element] = it }

  override fun toString(): String {
    return "ClassSetImpl(${initialClasses.contentToString()})"
  }

  override fun toList(): List<Class<out @UnsafeVariance T>> = listOf(*initialClasses)
}

fun <T> classSetOf(vararg classes: Class<out T>): ClassSet<T> =
  if (classes.isNotEmpty()) ClassSetImpl(*classes) else emptyClassSet

private val emptyClassSet: ClassSet<Nothing> = object : ClassSet<Nothing> {
  override fun isEmpty() = true
  override fun contains(element: Class<out Nothing>): Boolean = false
  override fun toList(): List<Class<out Nothing>> = emptyList()
}

fun <T> emptyClassSet(): ClassSet<T> = emptyClassSet

class ClassSetsWrapper<out T>(val sets: Array<ClassSet<@UnsafeVariance T>>) : ClassSet<T> {
  override fun isEmpty(): Boolean = sets.all { it.isEmpty() }
  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean = sets.any { it.contains(element) }
  override fun toList(): List<Class<out T>> = ContainerUtil.concat(*sets.map2Array { it.toList() })
}

private const val SIMPLE_CLASS_SET_LIMIT = 5