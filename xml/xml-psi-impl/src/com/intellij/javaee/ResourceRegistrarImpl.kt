// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import org.jetbrains.annotations.NonNls
import java.util.ArrayList
import java.util.HashMap

class ResourceRegistrarImpl : ResourceRegistrar {
  private val resources = HashMap<String, MutableMap<String, ExternalResource>>()
  private val ignored = ArrayList<String>()

  internal fun getIgnoredResources(): List<String> = ignored

  override fun addStdResource(resource: @NonNls String, fileName: @NonNls String, aClass: Class<*>?) {
    addStdResource(resource = resource, version = null, fileName = fileName, aClass = aClass)
  }

  fun addStdResource(
    resource: @NonNls String,
    version: @NonNls String?,
    fileName: @NonNls String,
    aClass: Class<*>?,
    classLoader: ClassLoader?
  ) {
    ExternalResourceManagerExImpl.getOrCreateMap(resources, version)
      .put(resource, ExternalResource(file = fileName, aClass = aClass, classLoader = classLoader))
  }

  override fun addStdResource(resource: @NonNls String, version: @NonNls String?, fileName: @NonNls String, aClass: Class<*>?) {
    addStdResource(resource = resource, version = version, fileName = fileName, aClass = aClass, classLoader = null)
  }

  override fun addStdResource(resource: @NonNls String, fileName: @NonNls String, classLoader: ClassLoader) {
    addStdResource(resource = resource, version = null, fileName = fileName, aClass = null, classLoader = classLoader)
  }

  override fun addIgnoredResource(url: @NonNls String) {
    ignored.add(url)
  }

  @Deprecated("Pass class loader explicitly", level = DeprecationLevel.ERROR)
  fun addInternalResource(resource: @NonNls String, fileName: @NonNls String?) {
    addStdResource(
      resource = resource,
      version = null,
      fileName = ExternalResourceManagerEx.STANDARD_SCHEMAS.trimStart('/') + fileName,
      aClass = null,
      classLoader = javaClass.classLoader,
    )
  }

  fun addInternalResource(resource: @NonNls String, fileName: @NonNls String?, classLoader: ClassLoader) {
    addInternalResource(resource = resource, version = null, fileName = fileName, classLoader = classLoader)
  }

  fun addInternalResource(resource: @NonNls String, version: @NonNls String?, fileName: @NonNls String?, classLoader: ClassLoader) {
    ExternalResourceManagerExImpl.getOrCreateMap(resources, version = version).put(resource, ExternalResource(
      file = ExternalResourceManagerEx.STANDARD_SCHEMAS.trimStart('/') + fileName,
      aClass = null,
      classLoader = classLoader,
    ))
  }

  internal fun getResources(): Map<String, MutableMap<String, ExternalResource>> = resources
}
