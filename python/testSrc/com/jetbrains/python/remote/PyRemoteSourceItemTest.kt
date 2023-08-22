// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote

import org.assertj.core.api.AutoCloseableSoftAssertions
import org.junit.Test

class PyRemoteSourceItemTest {
  @Test
  fun `test localPathForRemoteRoot`() {
    AutoCloseableSoftAssertions().use { softAssertions ->
      softAssertions
        .assertThat(PyRemoteSourceItem.localPathForRemoteRoot(".", "/opt/path/"))
        .describedAs("The result of `localPathForRemoteRoot()` must be independent of the presence of the trailing FS in `remoteRoot`")
        .isEqualTo(PyRemoteSourceItem.localPathForRemoteRoot(".", "/opt/path"))

      softAssertions
        .assertThat(PyRemoteSourceItem.localPathForRemoteRoot(".", "C:/Users/username/Path"))
        .describedAs("The result of `localPathForRemoteRoot()` must be independent of the FS character")
        .isEqualTo(PyRemoteSourceItem.localPathForRemoteRoot(".", "C:\\Users\\username\\Path"))
    }
  }
}