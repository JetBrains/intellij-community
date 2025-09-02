// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.util

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.map2Array
import java.util.concurrent.ConcurrentHashMap

interface ClassSet<out T> {
  fun isEmpty(): Boolean
  operator fun contains(element: Class<out @UnsafeVariance T>): Boolean
  fun toList(): List<Class<out @UnsafeVariance T>>
}

fun <T> T?.isInstanceOf(classSet: ClassSet<T>): Boolean =
  this?.let { classSet.contains(it.javaClass) } ?: false

fun <T> ClassSet<T>.hasClassOf(instance: T?): Boolean =
  instance?.let { contains(it.javaClass) } ?: false

private class ClassSetImpl<out T>(val initialClasses: List<Class<out T>>) : ClassSet<T> {
  private val internalMapping: MutableMap<String, Boolean> = ConcurrentHashMap<String, Boolean>().apply {
    for (initialClass in initialClasses) {
      this[initialClass.name] = true
    }
  }

  override fun isEmpty(): Boolean = initialClasses.isEmpty()

  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean {
    return internalMapping[element.name]
      ?: initialClasses.any { it.isAssignableFrom(element) }.also { internalMapping[element.name] = it }
  }

  override fun toString(): String {
    return "ClassSetImpl($initialClasses)"
  }

  override fun toList(): List<Class<out @UnsafeVariance T>> = initialClasses
}

private class ClassSetImpl1<out T>(val clazz: Class<out T>) : ClassSet<T> {
  private val list: List<Class<out T>> = listOf(clazz)

  override fun isEmpty(): Boolean = false

  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean {
    return clazz.isAssignableFrom(element)
  }

  override fun toString(): String {
    return "ClassSetImpl1($clazz)"
  }

  override fun toList(): List<Class<out @UnsafeVariance T>> = list
}

private class ClassSetImpl2<out T>(val aClazz: Class<out T>,
                                   val bClazz: Class<out T>) : ClassSet<T> {
  private val list: List<Class<out T>> = listOf(aClazz, bClazz)

  override fun isEmpty(): Boolean = false

  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean {
    return aClazz.isAssignableFrom(element)
           || bClazz.isAssignableFrom(element)
  }

  override fun toString(): String {
    return "ClassSetImpl2($aClazz, $bClazz)"
  }

  override fun toList(): List<Class<out @UnsafeVariance T>> = list
}

private class ClassSetImpl3<out T>(val aClazz: Class<out T>,
                                   val bClazz: Class<out T>,
                                   val cClazz: Class<out T>) : ClassSet<T> {
  private val list: List<Class<out T>> = listOf(aClazz, bClazz, cClazz)

  override fun isEmpty(): Boolean = false

  override operator fun contains(element: Class<out @UnsafeVariance T>): Boolean {
    return aClazz.isAssignableFrom(element)
           || bClazz.isAssignableFrom(element)
           || cClazz.isAssignableFrom(element)
  }

  override fun toString(): String {
    return "ClassSetImpl3($aClazz, $bClazz, $cClazz)"
  }

  override fun toList(): List<Class<out @UnsafeVariance T>> = list
}

fun <T> classSetOf(vararg classes: Class<out T>): ClassSet<T> {
  if (classes.isEmpty()) return emptyClassSet

  return when (classes.size) {
    0 -> emptyClassSet
    1 -> ClassSetImpl1(classes[0])
    2 -> ClassSetImpl2(classes[0], classes[1])
    3 -> ClassSetImpl3(classes[0], classes[1], classes[2])
    else -> ClassSetImpl(classes.toList())
  }
}

fun <T> classSetsUnion(vararg classSets: ClassSet<T>): ClassSet<T> = classSetOf(*classSets.flatMap { it.toList() }.toTypedArray())

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