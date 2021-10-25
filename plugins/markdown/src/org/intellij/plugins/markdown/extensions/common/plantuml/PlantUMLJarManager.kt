// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.concurrent.locks.ReentrantLock

/**
 * An application level service to support [PlantUMLCodeGeneratingProvider] extension.
 * Manages lifetime of loaded class loader for `plantuml.jar`.
 *
 * It could potentially support dynamic replacements of actual jar file
 * if [dropCache] is called before changing physical file in the file system.
 */
@Service(Service.Level.APP)
internal class PlantUMLJarManager: Disposable {
  private data class Holder(
    val loadedClass: Class<*>,
    val method: Method
  )

  private val lock = ReentrantLock()
  private var loadedClassAndMethod: Holder? = null

  private fun loadClass(path: File): Class<*>? {
    val classLoader = URLClassLoader(arrayOf(path.toURI().toURL()), this::class.java.classLoader)
    try {
      return Class.forName(className, false, classLoader)
    } catch (exception: Throwable) {
      logger.warn(
        "Failed to find $className class in downloaded PlantUML jar. Please try to download another PlantUML library version.",
        exception
      )
      classLoader.close()
      return null
    }
  }

  private fun findMethod(loadedClass: Class<*>): Method? {
    val methodName = "generateImage"
    try {
      return loadedClass.getDeclaredMethod(methodName, Class.forName("java.io.OutputStream"))
    } catch (exception: Throwable) {
      logger.warn(
        "Failed to find 'generateImage' method in the class '$className'. Please try to download another PlantUML library version.",
        exception
      )
      return null
    }
  }

  private fun findPath(): File? {
    val extension = MarkdownCodeFencePluginGeneratingProvider.all
      .filterIsInstance<PlantUMLCodeGeneratingProvider>()
      .firstOrNull()
    return extension?.fullPath
  }

  private fun obtainCurrentHolder(): Holder? {
    synchronized(lock) {
      if (Disposer.isDisposed(this)) {
        return null
      }
      if (loadedClassAndMethod == null) {
        val path = findPath() ?: return null
        val loadedClass = loadClass(path) ?: return null
        val method = findMethod(loadedClass) ?: return null
        loadedClassAndMethod = Holder(loadedClass, method)
      }
      return loadedClassAndMethod
    }
  }

  fun generateImage(source: String, outputStream: OutputStream) {
    synchronized(lock) {
      val (loadedClass, method) = obtainCurrentHolder() ?: return
      try {
        method.invoke(loadedClass.getConstructor(String::class.java).newInstance(source), outputStream)
      } catch (exception: Throwable) {
        logger.warn("Failed to invoke method.", exception)
      }
    }
  }

  fun dropCache() {
    synchronized(lock) {
      loadedClassAndMethod?.let {
        try {
          (it.loadedClass.classLoader as? URLClassLoader)?.close()
        } catch (exception: Throwable) {
          logger.warn("Failed to close class loader.", exception)
        }
      }
      loadedClassAndMethod = null
    }
  }

  override fun dispose() {
    dropCache()
  }

  companion object {
    private const val className = "net.sourceforge.plantuml.SourceStringReader"
    private val logger = logger<PlantUMLJarManager>()

    @JvmStatic
    fun getInstance(): PlantUMLJarManager {
      return service()
    }
  }
}
