package com.intellij.xml

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object XmlCoreBundle {
  const val BUNDLE: @NonNls String = "messages.XmlCoreBundle"
  private val INSTANCE = DynamicBundle(XmlCoreBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(
    @PropertyKey(resourceBundle = BUNDLE)
    key: String,
    vararg params: Any?,
  ): @Nls String {
    return INSTANCE.getMessage(key = key, params = params)
  }

  @JvmStatic
  fun messagePointer(
    @PropertyKey(resourceBundle = BUNDLE)
    key: String,
    vararg params: Any?,
  ): Supplier<String> {
    return INSTANCE.getLazyMessage(key = key, params = params)
  }
}
