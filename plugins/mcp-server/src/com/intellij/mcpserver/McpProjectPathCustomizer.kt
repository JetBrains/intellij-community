package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName

interface McpProjectPathCustomizer {
  val parameterName: String
  val parameterDescription: String
    get() = DEFAULT_PARAMETER_DESCRIPTION

  companion object {
    val EP: ExtensionPointName<McpProjectPathCustomizer> =
      ExtensionPointName("com.intellij.mcpServer.mcpProjectPathCustomizer")

    const val DEFAULT_PARAMETER_DESCRIPTION: String =
      "The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.\n" +
      "In the case you know only the current working directory you can use it as the project path.\n" +
      "If you're not aware about the project path you can ask user about it."
  }
}
