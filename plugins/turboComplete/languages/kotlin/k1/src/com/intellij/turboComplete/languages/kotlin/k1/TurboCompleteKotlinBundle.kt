package com.intellij.turboComplete.languages.kotlin.k1

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

class TurboCompleteKotlinBundle : DynamicBundle(TURBO_COMPLETE_JAVA_BUNDLE) {
  companion object {
    private val ourInstance: AbstractBundle = TurboCompleteKotlinBundle()

    private const val TURBO_COMPLETE_JAVA_BUNDLE = "messages.TurboCompleteKotlin"

    @Nls
    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = TURBO_COMPLETE_JAVA_BUNDLE) String, vararg params: Any): String {
      return ourInstance.getMessage(key, *params)
    }
  }
}
