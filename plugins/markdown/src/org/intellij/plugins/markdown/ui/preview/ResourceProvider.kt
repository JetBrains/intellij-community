// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import java.io.File
import kotlin.reflect.KClass

interface ResourceProvider {
  /**
   * Resource description, containing resource content and it's type.
   * In case type is null, [PreviewStaticServer] will try to guess it based
   * on resource name.
   */
  class Resource(
    val content: ByteArray,
    val type: String? = null
  )

  /**
   * @return true if this resource provider can load resource [resourceName]
   * with it's [loadResource] method.
   */
  fun canProvide(resourceName: String): Boolean

  /**
   * Load [resourceName] contents.
   *
   * @param resourceName Resource path.
   * @return [Resource] if resource was successfully loaded or null if load failed.
   */
  fun loadResource(resourceName: String): Resource?

  private class DefaultResourceProvider: ResourceProvider {
    override fun canProvide(resourceName: String): Boolean = false
    override fun loadResource(resourceName: String): Resource? = null
  }

  companion object {
    /**
     * Default resource provider implementation with
     * [canProvide] and [loadResource] returning always false and null.
     */
    val default: ResourceProvider = DefaultResourceProvider()

    /**
     * Load resource using [cls]'s [ClassLoader].
     *
     * @param cls Java class to get the [ClassLoader] of.
     * @param path Path of the resource to load.
     * @param contentType Explicit type of content. If null, [PreviewStaticServer] will
     * try to guess content type based on resource name.
     * @return [Resource] with the contents of resource, or null in case
     * the resource could not be loaded.
     */
    @JvmStatic
    fun <T> loadInternalResource(cls: Class<T>, path: String, contentType: String? = null): Resource? {
      return cls.getResourceAsStream(path)?.use {
        Resource(it.readBytes(), contentType)
      }
    }

    /**
     * Load resource using [cls]'s [ClassLoader].
     *
     * @param cls Kotlin class to get the [ClassLoader] of.
     * @param path Path of the resource to load.
     * @param contentType Explicit type of content. If null, [PreviewStaticServer] will
     * try to guess content type based on resource name.
     * @return [Resource] with the contents of resource, or null in case
     * the resource could not be loaded.
     */
    @JvmStatic
    fun <T : Any> loadInternalResource(cls: KClass<T>, path: String, contentType: String? = null): Resource? {
      return loadInternalResource(cls.java, path, contentType)
    }

    /**
     * See [loadInternalResource]
     */
    @JvmStatic
    inline fun <reified T : Any> loadInternalResource(path: String, contentType: String? = null): Resource? {
      return loadInternalResource(T::class.java, path, contentType)
    }

    /**
     * Load resource from the filesystem.
     *
     * @param file File to load.
     * @param contentType Explicit type of content. If null, [PreviewStaticServer] will
     * try to guess content type based on resource name.
     * @return [Resource] with the contents of resource, or null in case
     * the resource could not be loaded.
     */
    @JvmStatic
    fun loadExternalResource(file: File, contentType: String? = null): Resource? {
      if (!file.exists()) {
        return null
      }
      val content = file.inputStream().use { it.readBytes() }
      return Resource(content, contentType)
    }
  }
}
