// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.syntax

import com.intellij.platform.syntax.i18n.ResourceBundle
import org.jetbrains.annotations.*
import kotlin.jvm.JvmStatic

object XmlSyntaxBundle {
  const val BUNDLE: @NonNls String = "messages.XmlSyntaxBundle"

  val resourceBundle: ResourceBundle = run {
    val defaultMapping by lazy { DefaultXmlSyntaxResources.mappings }
    ResourceBundle(
      bundleClass = "com.intellij.xml.syntax.XmlSyntaxBundle",
      pathToBundle = BUNDLE,
      self = this,
      defaultMapping = defaultMapping
    )
  }

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return resourceBundle.message(key, *params)
  }

  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): () -> @Nls String {
    return resourceBundle.messagePointer(key, *params)
  }
}

@VisibleForTesting
@ApiStatus.Internal
object DefaultXmlSyntaxResourcesTestAccessor {
  val mappings: Map<String, String> get() = DefaultXmlSyntaxResources.mappings
  val defaultJavaSyntaxResourcesName: String get() = "com.intellij.xml.syntax.DefaultXmlSyntaxResources"
}
