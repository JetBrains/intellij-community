// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.requirement.PyRequirementRelation

internal object PyStubsSuggestions {
  //relation null if stubs should be the latest
  val SUPPORTED_STUBS = mapOf(
    PyPackageName.from("docutils") to (pyRequirement("types-docutils") to PyRequirementRelation.COMPATIBLE),
    PyPackageName.from("PyGObject") to (pyRequirement("PyGObject-stubs") to null),
    PyPackageName.from("PyQt5") to (pyRequirement("PyQt5-stubs") to PyRequirementRelation.COMPATIBLE),
    PyPackageName.from("pandas") to (pyRequirement("pandas-stubs") to PyRequirementRelation.COMPATIBLE),
    PyPackageName.from("celery") to (pyRequirement("celery-types") to null),
    PyPackageName.from("boto3") to (pyRequirement("boto3-stubs") to PyRequirementRelation.COMPATIBLE),
    PyPackageName.from("scipy") to (pyRequirement("scipy-stubs") to PyRequirementRelation.COMPATIBLE),
    PyPackageName.from("traits") to (pyRequirement("traits-stubs") to null),
    PyPackageName.from("djangorestframework") to (pyRequirement("djangorestframework-stubs") to null),
  )
}