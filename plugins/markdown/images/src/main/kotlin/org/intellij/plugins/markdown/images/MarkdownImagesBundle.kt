package org.intellij.plugins.markdown.images

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@ApiStatus.Internal
object MarkdownImagesBundle {
  private const val name = "messages.MarkdownImagesBundle"
  private val bundle = DynamicBundle(MarkdownImagesBundle::class.java, name)

  fun message(key: @PropertyKey(resourceBundle = name) String, vararg params: Any): @Nls String {
    return bundle.getMessage(key, *params)
  }

  fun messagePointer(key: @PropertyKey(resourceBundle = name) String, vararg params: Any): Supplier<String> {
    return bundle.getLazyMessage(key, *params)
  }
}
