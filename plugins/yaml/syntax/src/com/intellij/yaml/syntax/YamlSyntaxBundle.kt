package com.intellij.yaml.syntax

import com.intellij.platform.syntax.i18n.ResourceBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import kotlin.jvm.JvmStatic

object YamlSyntaxBundle {
  const val BUNDLE: String = "messages.YamlSyntaxBundle"

  val resourceBundle: ResourceBundle by lazy {
    ResourceBundle(
      bundleClass = "org.jetbrains.yaml.syntax.YamlSyntaxBundle",
      pathToBundle = BUNDLE,
      self = this,
      defaultMapping = DefaultYamlSyntaxResources.mappings,
    )
  }

  @JvmStatic
  fun message(
    key: @PropertyKey(resourceBundle = BUNDLE) String,
    vararg params: Any,
  ): @Nls String {
    return resourceBundle.message(key, *params)
  }

  @JvmStatic
  fun messagePointer(
    key: @PropertyKey(resourceBundle = BUNDLE) String,
    vararg params: Any,
  ): () -> @Nls String {
    return resourceBundle.messagePointer(key, *params)
  }
}