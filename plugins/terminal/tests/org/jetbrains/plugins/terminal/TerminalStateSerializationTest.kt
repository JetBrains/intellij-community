// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.configurationStore.xml.testSerializer
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import org.junit.Test

class TerminalStateSerializationTest {
  @Test
  fun envs() {
    val state = TerminalOptionsProvider.State()
    state.envDataOptions.set(EnvironmentVariablesData.create(mapOf("foo" to "bar", "env2" to "true"), false))

    testSerializer("""
  <State>
    <envs>
      <env key="foo" value="bar" />
      <env key="env2" value="true" />
    </envs>
    <option name="passParentEnvs" value="false" />
  </State>
    """.trimIndent(), state, SkipDefaultsSerializationFilter())
  }
}
