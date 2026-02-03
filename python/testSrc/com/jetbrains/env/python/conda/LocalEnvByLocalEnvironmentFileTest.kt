// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.testFramework.ProjectRule
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

internal class LocalEnvByLocalEnvironmentFileTest {

  private val condaRule: LocalCondaRule = LocalCondaRule()

  private val yamlRule: CondaYamlFileRule = CondaYamlFileRule(condaRule)

  @Rule
  @JvmField
  internal val chain = RuleChain.outerRule(ProjectRule()).around(condaRule).around(yamlRule)

  @Test
  fun parseYaml() {
    val file = yamlRule.yamlFilePath
    Assert.assertEquals("Wrong name parsed out of yaml file", yamlRule.envName,
                        NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(file, emptyList()).envName)
  }
}