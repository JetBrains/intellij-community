package com.github.firsttimeinforever.mermaid

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.MermaidBundle"

object MermaidBundle : DynamicBundle(BUNDLE) {
  @Suppress("SpreadOperator")
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String {
    return getMessage(key, *params)
  }

  @Suppress("SpreadOperator", "unused")
  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> {
    return getLazyMessage(key, *params)
  }
}
