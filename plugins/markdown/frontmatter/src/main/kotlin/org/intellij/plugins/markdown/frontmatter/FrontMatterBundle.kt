package org.intellij.plugins.markdown.frontmatter

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.FrontMatterBundle"

internal object FrontMatterBundle: DynamicBundle(BUNDLE) {
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String {
    return getMessage(key, *params)
  }

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> {
    return getLazyMessage(key, *params)
  }
}
