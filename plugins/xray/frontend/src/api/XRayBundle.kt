// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.api

import com.intellij.DynamicBundle
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE: String = "messages.XRayBundle"

/**
 * A bundle for X-Ray mode constants
 */
@ApiStatus.Experimental
object XRayBundle {
  private val instance = DynamicBundle(XRayBundle::class.java, BUNDLE)

  @Nls
  @JvmStatic
  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = instance.getMessage(key, *params)

  @Nls
  @JvmStatic
  fun messageOrNull(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String? = instance.messageOrNull(key, *params)

  @Nls
  @JvmStatic
  fun htmlMessage(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return XmlStringUtil.wrapInHtml(instance.getMessage(key, *params))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use messagePointer instead", ReplaceWith("messagePointer"))
  fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): () -> String {
    return { instance.getMessage(key, *params) }
  }

  @Nls
  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> {
    return instance.getLazyMessage(key, *params)
  }
}
