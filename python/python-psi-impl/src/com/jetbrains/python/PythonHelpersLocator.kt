/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PathUtil
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.Path

abstract class PythonHelpersLocator {
  companion object {
    val LOG = Logger.getInstance(PythonHelpersLocator::class.java)
    private const val PROPERTY_HELPERS_LOCATION = "idea.python.helpers.path"
    const val COMMUNITY_HELPERS_MODULE_NAME = "intellij.python.helpers"
    private val EP_NAME: ExtensionPointName<PythonHelpersLocator> =
      ExtensionPointName.create("com.jetbrains.python.pythonHelpersLocator")

    /**
     * @return A list of Path objects representing the roots of the Python Helpers.
     */
    @JvmStatic
    fun getHelpersRoots(): List<Path> = EP_NAME.extensionList.first().getRoots()

    /**
     * Retrieves the root file of the Community Helpers extension.
     * Community Helpers root always should be first in an extension list
     *
     * @return The root File of the Community Helpers extension.
     */
    @JvmStatic
    fun getCommunityHelpersRoot(): File = EP_NAME.extensionList.first().getRoots().first().toFile()

    /**
     * Retrieves File of a helper file given its resource name.
     *
     * @param resourceName The name of the helper resource file.
     * @return File of the helper file, or null if the file does not exist.
     */
    @JvmStatic
    fun findFileInHelpers(resourceName: String): File? {
      for (helperRoot in getHelpersRoots()) {
        val file = File(helperRoot.toFile(), resourceName)
        if (file.exists())
          return file
      }

      LOG.warn("File $resourceName does not exist in helpers root")
      return null
    }

    /**
     * Retrieves the absolute path of a helper file given its resource name.
     *
     * @param resourceName The name of the helper resource file.
     * @return The absolute path of the helper file, or null if the file does not exist.
     */
    @JvmStatic
    fun findPathInHelpers(@NonNls resourceName: String): String = findFileInHelpers(resourceName)?.absolutePath ?: ""

    @JvmStatic
    fun getPythonCommunityPath(): String {
      val pathFromUltimate = File(PathManager.getHomePath(), "community/python")
      if (pathFromUltimate.exists()) {
        return pathFromUltimate.path
      }
      return File(PathManager.getHomePath(), "python").path
    }

    @Deprecated("Use {@link PythonHelpersLocator#findPathInHelpers}.", ReplaceWith("findPathInHelpers(resourceName)"))
    @JvmStatic
    fun getHelperPath(@NonNls resourceName: String): String = findPathInHelpers(resourceName)
  }

  // Always add Community Helpers root on the first place of root's list
  protected open fun getRoots(): List<Path> = listOf(getCommunityHelpersRootFile().toPath().normalize())

  private fun getCommunityHelpersRootFile(): File {
    val property = System.getProperty(PROPERTY_HELPERS_LOCATION)
    return if (property != null) {
      File(property)
    }
    else getHelpersRoot(COMMUNITY_HELPERS_MODULE_NAME, "/python/helpers").also {
      assertHelpersLayout(it)
    }
  }

  protected open fun getHelpersRoot(moduleName: String, relativePath: String): File =
    findRootByJarPath(PathUtil.getJarPathForClass(PythonHelpersLocator::class.java), moduleName, relativePath)


  protected fun findRootByJarPath(jarPath: String, moduleName: String, relativePath: String): File {
    return if (PluginManagerCore.isRunningFromSources()) {
      File(PathManager.getCommunityHomePath() + relativePath)
    }
    else {
      getPluginBaseDir(jarPath)?.let {
        File(it, PathUtil.getFileName(relativePath))
      } ?: File(File(jarPath).parentFile, moduleName)
    }
  }

  private fun getPluginBaseDir(jarPath: String): File? {
    if (jarPath.endsWith(".jar")) {
      val jarFile = File(jarPath)

      LOG.assertTrue(jarFile.exists(), "jar file cannot be null")
      return jarFile.parentFile.parentFile
    }

    return null
  }

  private fun assertHelpersLayout(root: File): File {
    val path = root.absolutePath

    LOG.assertTrue(root.exists(), "Helpers root does not exist $path")
    listOf("generator3", "pycharm", "pycodestyle.py", "pydev", "syspath.py", "typeshed").forEach { child ->
      LOG.assertTrue(File(root, child).exists(), "No '$child' inside $path")
    }

    return root
  }
}

internal class PythonHelpersLocatorDefault : PythonHelpersLocator()