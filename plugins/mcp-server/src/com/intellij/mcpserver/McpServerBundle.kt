package com.intellij.mcpserver

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

const val PATH_TO_BUNDLE: @NonNls String = "messages.McpServerBundle"

internal object McpServerBundle : DynamicBundle(McpServerBundle::class.java, PATH_TO_BUNDLE) {
  fun message(key: @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String, vararg params: Any): @Nls String {
    return getMessage(key, *params)
  }

  fun messagePointer(
    key: @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String,
    vararg params: Any
  ): Supplier<String> {
    return getLazyMessage(key, params)
  }
}
