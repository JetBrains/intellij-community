// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import org.junit.Assert
import org.junit.Test

class LocalEnvByLocalEnvironmentFileTest {
  @Test
  fun parseYaml() {
    Assert.assertEquals("Wrong name parsed out of yaml file", yamlEnvName, NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(yamlFile).envName)
  }
}