// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import org.assertj.core.api.AutoCloseableSoftAssertions
import org.junit.Test
import java.io.File

class PlainEnvironmentControllerTest {
  @Test
  fun `test putFixedValue method`() {
    val envs = hashMapOf<String, String>()
    val controller = PlainEnvironmentController(envs)
    AutoCloseableSoftAssertions().use { softAssertions ->
      val envName = "FIXED_ENV_NAME"

      "Fixed ENV value".let { value ->
        controller.putFixedValue(envName, value)
        softAssertions
          .assertThat(envs[envName])
          .isEqualTo(value)
      }

      "Fixed ENV value override".let { value ->
        controller.putFixedValue(envName, value)
        softAssertions
          .assertThat(envs[envName])
          .describedAs("putFixedValue() must override the existing environment variable value")
          .isEqualTo(value)
      }
    }
  }

  @Test
  fun `test putTargetPathValue method`() {
    val envs = hashMapOf<String, String>()
    val controller = PlainEnvironmentController(envs)
    AutoCloseableSoftAssertions().use { softAssertions ->
      val envName = "SOME_PATH_ENV"

      "some/local/path".let { path ->
        controller.putTargetPathValue(envName, path)
        softAssertions
          .assertThat(envs[envName])
          .isEqualTo(path)
      }

      "another/local/path".let { path ->
        controller.putTargetPathValue(envName, path)
        softAssertions
          .assertThat(envs[envName])
          .describedAs("putTargetPathValue() must override the existing environment variable value")
          .isEqualTo(path)
      }
    }
  }

  @Test
  fun `test appendTargetPathToPathsValue method`() {
    val envs = hashMapOf<String, String>()
    val controller = PlainEnvironmentController(envs)
    AutoCloseableSoftAssertions().use { softAssertions ->
      val envName = "PYTHONPATH"

      val firstPath = "/first/path"
      controller.appendTargetPathToPathsValue(envName, firstPath)
      softAssertions.assertThat(envs[envName]).isEqualTo(firstPath)

      val additionalPath = "/additional/path"
      controller.appendTargetPathToPathsValue(envName, additionalPath)
      softAssertions.assertThat(envs[envName]).isEqualTo("$firstPath${File.pathSeparator}$additionalPath")
    }
  }
}