// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

class PoetryDependenciesCompletionTest : PythonDependencyCompletionTest() {

  fun testPackageNameCompletion() {
    mockPackageNames(listOf("mypy", "mypy-extensions"))

    completeInTomlFile()
    checkCompletionResults("mypy", "mypy-extensions")
  }

  fun testVersionCompletion() {
    mockPackageDetails("mypy-extensions", listOf("1.0.0", "0.4.4"))

    completeInTomlFile()
    checkCompletionResultsOrdered("\"1.0.0\"", "\"0.4.4\"")
  }

  override fun getBasePath(): String {
    return super.getBasePath() + "poetry/"
  }
}