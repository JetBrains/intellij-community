// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Rule
import org.junit.Test


class PythonSdkTypeTest {
  @JvmField
  @Rule
  val softly = JUnitSoftAssertions()

  @Test
  fun testIsCustomPythonSdkHomePath() {
    softly
      .assertThat(PythonSdkType.isCustomPythonSdkHomePath("\\\\wsl$\\Debian\\usr\\bin\\python"))
      .describedAs("Custom Python SDK home path prefix may point to the wsl")
      .isTrue
    softly
      .assertThat(PythonSdkType.isCustomPythonSdkHomePath("docker://python:latest/python"))
      .describedAs("Custom Python SDK home path prefix might contain latin characters")
      .isTrue
    softly
      .assertThat(PythonSdkType.isCustomPythonSdkHomePath("docker-compose://[/home/user/project/docker-compose.yml]:app/python"))
      .describedAs("Custom Python SDK home path prefix might contain hyphen character")
      .isTrue
    softly
      .assertThat(PythonSdkType.isCustomPythonSdkHomePath("C:\\Users\\User\\AppData\\Local\\Programs\\Python\\Python37\\python.exe"))
      .describedAs("Python SDK with Windows-like home path must not be treated as a custom Python SDK")
      .isFalse
  }
}