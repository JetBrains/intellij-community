// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.annotations.Attribute

class CommandSpecsBean : PluginAware {
  /** Path of the short command specs JSON file inside JAR */
  @Attribute("path")
  lateinit var path: String

  /** Path of the command specs inside JAR */
  val basePath: String
    get() = path.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }

  lateinit var pluginDesc: PluginDescriptor

  override fun toString(): String {
    return "${javaClass.simpleName} { path: $path }"
  }

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    pluginDesc = pluginDescriptor
  }

  companion object {
    val EP_NAME = ExtensionPointName<CommandSpecsBean>("org.jetbrains.plugins.terminal.commandSpecs")
  }
}