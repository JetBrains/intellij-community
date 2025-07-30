// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs

import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.requirement.PyRequirementRelation

internal object PyStubsSuggestions {
  //relation null if stubs should be the latest
  val SUPPORTED_STUBS = mapOf(
    NormalizedPythonPackageName.from("docutils") to (pyRequirement("types-docutils") to PyRequirementRelation.COMPATIBLE),
    NormalizedPythonPackageName.from("PyGObject") to (pyRequirement("PyGObject-stubs") to null),
    NormalizedPythonPackageName.from("PyQt5") to (pyRequirement("PyQt5-stubs") to PyRequirementRelation.COMPATIBLE),
    NormalizedPythonPackageName.from("pandas") to (pyRequirement("pandas-stubs") to PyRequirementRelation.COMPATIBLE),
    NormalizedPythonPackageName.from("celery") to (pyRequirement("celery-types") to null),
    NormalizedPythonPackageName.from("boto3") to (pyRequirement("boto3-stubs") to PyRequirementRelation.COMPATIBLE),
    NormalizedPythonPackageName.from("scipy") to (pyRequirement("scipy-stubs") to PyRequirementRelation.COMPATIBLE),
    NormalizedPythonPackageName.from("traits") to (pyRequirement("traits-stubs") to null),
    NormalizedPythonPackageName.from("djangorestframework") to (pyRequirement("djangorestframework-stubs") to null),
  )
}