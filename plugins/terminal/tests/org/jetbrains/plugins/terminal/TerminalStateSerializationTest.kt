// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.configurationStore.xml.testSerializer
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import org.junit.Test

class TerminalStateSerializationTest {
  @Test
  fun envs() {
    val state = TerminalProjectOptionsProvider.State()
    state.envDataOptions.set(EnvironmentVariablesData.create(linkedMapOf("B" to "bar", "A" to "true"), false))

    testSerializer("""
    <State>
      <envs>
        <env key="B" value="bar" />
        <env key="A" value="true" />
      </envs>
      <option name="passParentEnvs" value="false" />
    </State>
    """, state, SkipDefaultsSerializationFilter())
  }
}
