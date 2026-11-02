// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.jps.api.JpsDynamicBundle

@NonNls
private const val BUNDLE = "messages.KotlinJpsBuilderBundle"

/** The messages of the Kotlin JPS builder. */
object KotlinJpsBuilderBundle {
  private val instance = JpsDynamicBundle(KotlinJpsBuilderBundle::class.java, BUNDLE)

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = instance.getMessage(key, *params)
}
