// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests

import com.intellij.configurationStore.xml.testSerializer
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.junit.Test

class TerminalStateSerializationTest {
  @Test
  fun envs() {
    val state = TerminalProjectOptionsProvider.State()
    state.envDataOptions.set(EnvironmentVariablesData.create(linkedMapOf("B" to "bar", "A" to "true"), false))

    testSerializer(
      expectedXml = """
        <State>
          <envs>
            <env key="B" value="bar" />
            <env key="A" value="true" />
          </envs>
          <option name="passParentEnvs" value="false" />
        </State>
      """,
      expectedJson = """
        {
          "envDataOptions": {
            "envs": {
              "B": "bar",
              "A": "true"
            },
            "passParentEnvs": false
          }
        }
      """,
      bean = state,
      filter = SkipDefaultsSerializationFilter(),
    )
  }
}
