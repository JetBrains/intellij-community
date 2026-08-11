package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object PyNumericTowerUtil {

  private val ALLOWED_CLASSES: List<String> = listOf(PyNames.FQN.FLOAT, PyNames.FQN.COMPLEX)

  @JvmStatic
  val isEnabled: Boolean get() = Registry.`is`("python.numeric.tower.type")

  @JvmStatic
  fun enrich(type: PyType?): PyType? {
    PyAnyType.validate(type)
    if (!isEnabled) return type
    if (type is PyCompositeType) {
      val mappedMembers = type.members.map { typeArg -> expand(typeArg) }
      return when (type) {
        is PyUnionType -> PyUnionType.union(mappedMembers)
        is PyIntersectionType -> PyIntersectionType.intersection(mappedMembers)
        is PyUnsafeUnionType -> PyUnsafeUnionType.unsafeUnion(mappedMembers)
        else -> throw IllegalStateException("Unknown type: ${type.name}")
      }
    }
    return expand(type)
  }


  private fun expand(clsType: PyType?): PyType? {
    if (clsType !is PyClassType || clsType.classQName !in ALLOWED_CLASSES) return clsType
    if (clsType.isDefinition) return clsType

    val source = clsType.pyClass
    val classQName = clsType.classQName

    val floatType = PyBuiltinCache.getInstance(source).floatType
    val intType = PyBuiltinCache.getInstance(source).intType

    val members = when (classQName) {
      PyNames.FQN.FLOAT -> listOf(floatType, intType)
      PyNames.FQN.COMPLEX -> listOf(PyBuiltinCache.getInstance(source).complexType, floatType, intType)
      else -> throw IllegalStateException("Not a numeric tower type: $classQName")
    }
    return PyUnionType.union(members)
  }
}