package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import org.jetbrains.annotations.ApiStatus

/**
 * A specialized class type representing Python's numeric tower (int -> float -> complex).
 * It allows declaring widening promotions that may be unpacked when needed for type narrowing.
 */
@ApiStatus.Experimental
class PyNumericTowerType private constructor(
  source: PyClass,
  isDefinition: Boolean,
) : PyClassTypeImpl(source, isDefinition) {

  val hiddenUnion: PyUnionType = run {
    val floatType = PyBuiltinCache.getInstance(source).floatType
    val intType = PyBuiltinCache.getInstance(source).intType

    when (classQName) {
      PyNames.TYPE_FLOAT -> listOf(floatType, intType)
      PyNames.TYPE_COMPLEX -> listOf(PyBuiltinCache.getInstance(source).complexType, floatType, intType)
      else -> throw IllegalStateException("Not a numeric tower type: $classQName")
    }
  }.let { PyUnionType.union(it) } as PyUnionType

  override fun toInstance(): PyClassType {
    if (!myIsDefinition) return this
    if (userMap.isEmpty) {
      return getCachedTowerType(myClass, false)
    }
    return withUserDataCopy(PyNumericTowerType(myClass, false))
  }

  override fun toClass(): PyClassType {
    if (myIsDefinition) return this
    if (userMap.isEmpty) {
      return getCachedTowerType(myClass, true)
    }
    return withUserDataCopy(PyNumericTowerType(myClass, true))
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyNumericTowerType(this)
    }
    return super.acceptTypeVisitor(visitor)
  }

  companion object {
    private val ALLOWED_CLASSES: List<String> = listOf(PyNames.TYPE_FLOAT, PyNames.TYPE_COMPLEX)

    @JvmStatic
    val isEnabled: Boolean get() = Registry.`is`("python.numeric.tower.type")

    private fun getCachedTowerType(source: PyClass, isDefinition: Boolean): PyNumericTowerType {
      return PyUtil.getParameterizedCachedValue(source, isDefinition) { isDef -> PyNumericTowerType(source, isDef) }
    }

    @JvmStatic
    fun enrich(type: PyType?): PyType? {
      return type.toStream().map {
        if (isEnabled && it is PyClassType && it.classQName in ALLOWED_CLASSES) {
          getCachedTowerType(it.pyClass, it.isDefinition)
        }
        else it
      }.collect(PyTypeUtil.toUnion(type))
    }

    @JvmStatic
    fun unpackToUnion(type: PyType?): PyType? {
      return type.toStream()
        .map { if (it is PyNumericTowerType) it.hiddenUnion else it }
        .collect(PyTypeUtil.toUnion(type))
    }
  }
}

internal fun PyType?.isFloatTower(): Boolean =
  this is PyNumericTowerType && this.classQName == PyNames.TYPE_FLOAT

internal fun PyType?.isComplexTower(): Boolean =
  this is PyNumericTowerType && this.classQName == PyNames.TYPE_COMPLEX

internal fun PyType?.isPlainFloat(): Boolean =
  this is PyClassType && this !is PyNumericTowerType && this.classQName == PyNames.TYPE_FLOAT

internal fun PyType?.isPlainComplex(): Boolean =
  this is PyClassType && this !is PyNumericTowerType && this.classQName == PyNames.TYPE_COMPLEX