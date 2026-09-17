package com.intellij.lsp.ui

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.LspUiBundle"

internal object LspUiBundle {
  private val instance = DynamicBundle(LspUiBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return instance.getMessage(key, *params)
  }

  @JvmStatic
  fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> {
    return instance.getLazyMessage(key, *params)
  }
}