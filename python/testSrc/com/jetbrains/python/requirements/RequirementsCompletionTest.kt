// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

class RequirementsCompletionTest : PythonDependencyCompletionTest() {

  fun testPackageNameCompletion() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    completeInRequirementsFile()
    checkCompletionResults("mypy", "mypy-extensions")
  }

  fun testPackageNameInProjectTablePyProjectToml() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    completeInTomlFile()
    checkCompletionResults("mypy", "mypy-extensions")
  }

  fun testPackageNameInBuildSystemTablePyProjectToml() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    completeInTomlFile()
    checkCompletionResults("mypy", "mypy-extensions")
  }

  fun testVersionCompletion() {
    mockPackageDetails("mypy", listOf("1.7.0", "1.6.1", "1.6.0"))

    completeInRequirementsFile()
    checkCompletionResultsOrdered("1.7.0", "1.6.1", "1.6.0")
  }

  fun testVersionInBuildSystemTablePyProjectToml() {
    mockPackageDetails("mypy", listOf("1.7.0", "1.6.1", "1.6.0"))

    completeInTomlFile()
    checkCompletionResultsOrdered("1.7.0", "1.6.1", "1.6.0")
  }

  fun testVersionInProjectTablePyProjectToml() {
    mockPackageDetails("mypy", listOf("1.7.0", "1.6.1", "1.6.0"))

    completeInTomlFile()
    checkCompletionResultsOrdered("1.7.0", "1.6.1", "1.6.0")
  }

  override fun getBasePath(): String {
    return super.getBasePath() + "completion/"
  }
}