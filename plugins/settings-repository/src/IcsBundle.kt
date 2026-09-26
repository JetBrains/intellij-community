// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal const val BUNDLE = "messages.IcsBundle"

internal object IcsBundle {
  private val bundle = DynamicBundle(IcsBundle::class.java, BUNDLE)

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = bundle.getMessage(key, *params)
}

@Nls
internal fun icsMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
  return IcsBundle.message(key, *params)
}
