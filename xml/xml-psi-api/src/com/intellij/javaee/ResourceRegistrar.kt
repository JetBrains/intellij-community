// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import org.jetbrains.annotations.NonNls

/**
 * @see StandardResourceProvider
 */
interface ResourceRegistrar {
  fun addStdResource(resource: @NonNls String, fileName: @NonNls String, classLoader: ClassLoader)

  @Deprecated("Pass classLoader explicitly", ReplaceWith("addStdResource(resource, fileName, classLoader)"))
  fun addStdResource(resource: @NonNls String, fileName: @NonNls String, klass: Class<*>?)

  fun addStdResource(resource: @NonNls String, version: @NonNls String?, fileName: @NonNls String, aClass: Class<*>?)

  fun addIgnoredResource(url: @NonNls String)
}
