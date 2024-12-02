package com.intellij.xml.parsing

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object XmlParserBundle {
  const val BUNDLE: @NonNls String = "messages.XmlParserBundle"
  private val INSTANCE = DynamicBundle(XmlParserBundle::class.java, BUNDLE)

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
