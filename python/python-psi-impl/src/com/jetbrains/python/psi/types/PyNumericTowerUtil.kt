package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object PyNumericTowerUtil {

  private val ALLOWED_CLASSES: List<String> = listOf(PyNames.TYPE_FLOAT, PyNames.TYPE_COMPLEX)

  @JvmStatic
  val isEnabled: Boolean get() = Registry.`is`("python.numeric.tower.type")

  @JvmStatic
  fun enrich(type: PyType?): PyType? {
    if (!isEnabled) return type
    return type.toStream().map(::expand).collect(PyTypeUtil.toUnion(type))
  }


  private fun expand(clsType: PyType?): PyType? {
    if (clsType !is PyClassType || clsType.classQName !in ALLOWED_CLASSES) return clsType

    val source = clsType.pyClass
    val classQName = clsType.classQName

    val floatType = PyBuiltinCache.getInstance(source).floatType
    val intType = PyBuiltinCache.getInstance(source).intType

    val members = when (classQName) {
      PyNames.TYPE_FLOAT -> listOf(floatType, intType)
      PyNames.TYPE_COMPLEX -> listOf(PyBuiltinCache.getInstance(source).complexType, floatType, intType)
      else -> throw IllegalStateException("Not a numeric tower type: $classQName")
    }
    return PyUnionType.union(members)
  }
}