// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import org.jetbrains.annotations.NonNls
import java.util.ArrayList
import java.util.HashMap

class ResourceRegistrarImpl : ResourceRegistrar {
  private val resources = HashMap<String, MutableMap<String, ExternalResourceManagerExImpl.Resource>>()
  private val ignored = ArrayList<String>()

  internal fun getIgnoredResources(): List<String> = ignored

  override fun addStdResource(resource: @NonNls String, fileName: @NonNls String) {
    addStdResource(resource = resource, version = null, fileName = fileName, aClass = javaClass)
  }

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
    ExternalResourceManagerExImpl.getOrCreateMap<ExternalResourceManagerExImpl.Resource>(resources, version)
      .put(resource, ExternalResourceManagerExImpl.Resource(file = fileName, aClass = aClass, classLoader = classLoader))
  }

  override fun addStdResource(resource: @NonNls String, version: @NonNls String?, fileName: @NonNls String, aClass: Class<*>?) {
    addStdResource(resource = resource, version = version, fileName = fileName, aClass = aClass, classLoader = null)
  }

  override fun addIgnoredResource(url: @NonNls String) {
    ignored.add(url)
  }

  fun addInternalResource(resource: @NonNls String, fileName: @NonNls String?) {
    addInternalResource(resource = resource, version = null, fileName = fileName, aClass = javaClass)
  }

  fun addInternalResource(resource: @NonNls String, fileName: @NonNls String?, aClass: Class<*>?) {
    addInternalResource(resource = resource, version = null, fileName = fileName, aClass = aClass)
  }

  @JvmOverloads
  fun addInternalResource(resource: @NonNls String, version: @NonNls String?, fileName: @NonNls String?, aClass: Class<*>? = javaClass) {
    addStdResource(
      resource = resource,
      version = version,
      fileName = ExternalResourceManagerEx.STANDARD_SCHEMAS + fileName,
      aClass = aClass,
      classLoader = null,
    )
  }

  internal fun getResources(): Map<String, MutableMap<String, ExternalResourceManagerExImpl.Resource>> = resources
}
