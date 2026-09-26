// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements

import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarker
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType
import com.jetbrains.python.packaging.requirement.PyRequirementRelation

enum class PyRequirementEnvMarkerRelation(val text: String) {

  LT("<"),
  LTE("<="),
  GT(">"),
  GTE(">="),
  EQ("=="),
  NE("!="),
  COMPATIBLE("~="),
  STR_EQ("==="),
  IN("in"),
  NOTIN("not in");

  companion object {
    private val map = entries.associateBy(PyRequirementEnvMarkerRelation::text)

    @JvmStatic
    fun get(value: String): PyRequirementEnvMarkerRelation? = map[value]
  }
}

data class PyRequirementEnvMarkerImpl(
  val type: PyRequirementEnvMarkerType,
  val relation: PyRequirementEnvMarkerRelation,
  val values: List<String>
) : PyRequirementEnvMarker {
  override fun matches(platformData: Map<PyRequirementEnvMarkerType, String>): Boolean {
    val platformValue = platformData[type] ?: return true
    when (relation) {
      PyRequirementEnvMarkerRelation.STR_EQ -> return values[0] == platformValue
      PyRequirementEnvMarkerRelation.IN -> return values.contains(platformValue)
      PyRequirementEnvMarkerRelation.NOTIN -> return !values.contains(platformValue)
      else -> {}
    }

    if (type in VERSION_COMPARE_TYPES) {
      val versionSpec = pyRequirementVersionSpec(PyRequirementRelation.valueOf(relation.name), values[0])
      return versionSpec.matches(platformValue)
    }

    return when (relation) {
      PyRequirementEnvMarkerRelation.LT -> platformValue <= values[0]
      PyRequirementEnvMarkerRelation.LTE -> platformValue <= values[0]
      PyRequirementEnvMarkerRelation.GT -> platformValue > values[0]
      PyRequirementEnvMarkerRelation.GTE -> platformValue >= values[0]
      PyRequirementEnvMarkerRelation.EQ -> platformValue == values[0]
      PyRequirementEnvMarkerRelation.NE -> platformValue != values[0]
      else -> false
    }
  }

  companion object {
    private val VERSION_COMPARE_TYPES = setOf(
      PyRequirementEnvMarkerType.PYTHON_VERSION,
      PyRequirementEnvMarkerType.PYTHON_FULL_VERSION,
      PyRequirementEnvMarkerType.IMPLEMENTATION_VERSION
    )
  }
}

data class PyRequirementEnvMarkerAndSet(
  val markers: List<PyRequirementEnvMarker>
) : PyRequirementEnvMarker {
  override fun matches(platformData: Map<PyRequirementEnvMarkerType, String>): Boolean {
    return markers.all { it.matches(platformData) }
  }
}

data class PyRequirementEnvMarkerOrSet(
  val markers: List<PyRequirementEnvMarker>
) : PyRequirementEnvMarker {
  override fun matches(platformData: Map<PyRequirementEnvMarkerType, String>): Boolean {
    return markers.any { it.matches(platformData) }
  }
}
