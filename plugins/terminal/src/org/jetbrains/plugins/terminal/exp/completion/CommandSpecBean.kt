// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.xmlb.annotations.Attribute

class CommandSpecBean : PluginAware {
  @Attribute("command")
  lateinit var command: String

  @Attribute("path")
  lateinit var path: String

  lateinit var pluginDesc: PluginDescriptor

  override fun toString(): String {
    return "${javaClass.simpleName} { command: $command, path: $path }"
  }

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    pluginDesc = pluginDescriptor
  }

  companion object {
    val EP_NAME = ExtensionPointName<CommandSpecBean>("org.jetbrains.plugins.terminal.commandSpec")
  }
}