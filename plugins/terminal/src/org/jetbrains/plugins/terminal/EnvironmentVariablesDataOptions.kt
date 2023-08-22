// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.configurationStore.Property
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap

@Tag("")
class EnvironmentVariablesDataOptions : BaseState() {
  // user order of env must be preserved - do not sort user input
  @Property(description = "Environment variables")
  @get:XMap(entryTagName = "env", keyAttributeName = "key")
  val envs by linkedMap<String, String>()

  var isPassParentEnvs by property(true)

  fun set(envData: EnvironmentVariablesData) {
    envs.clear()
    envs.putAll(envData.envs)
    isPassParentEnvs = envData.isPassParentEnvs
    incrementModificationCount()
  }

  fun get(): EnvironmentVariablesData = EnvironmentVariablesData.create(envs, isPassParentEnvs)
}
