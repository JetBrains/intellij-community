package com.intellij.searchEverywhereMl.typos

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey


private const val BUNDLE = "messages.searchEverywhereMlTyposBundle"

internal object TyposBundle {
  private val instance = DynamicBundle(TyposBundle::class.java, BUNDLE)

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String = instance.getMessage(key, *params)
}
