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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.pathString

interface PythonHelpersLocator {
  companion object {
    val LOG: Logger = logger<PythonHelpersLocator>()
    private const val PROPERTY_HELPERS_LOCATION = "idea.python.helpers.path"
    const val COMMUNITY_HELPERS_MODULE_NAME = "intellij.python.helpers"
    private val EP_NAME: ExtensionPointName<PythonHelpersLocator> = ExtensionPointName("com.jetbrains.python.pythonHelpersLocator")

    /**
     * @return A list of Path objects representing the roots of the Python Helpers.
     */
    @Internal
    @JvmStatic
    fun getHelpersRoots(): List<Path> = EP_NAME.extensionList.mapNotNull { it.getRoot() }

    /**
     * Retrieves a path to the root file of the Community Helpers extension.
     *
     * @return The root Path of the Community Helpers extension.
     */
    @Internal
    @JvmStatic
    fun getCommunityHelpersRoot(): Path = EP_NAME.extensionList.first().getCommunityHelpersRootPath()

    /**
     * Retrieves Path of a helper file given its resource name.
     *
     * @param resourceName The name of the helper resource file.
     * @return Path of the helper file, or null if the file does not exist.
     */
    @JvmStatic
    @RequiresBackgroundThread
    fun findPathInHelpers(resourceName: String): Path? {
      for (helperRoot in getHelpersRoots()) {
        val path = Path.of(helperRoot.pathString, resourceName)
        if (path.exists())
          return path
      }

      LOG.info("File $resourceName does not exist in helpers root")
      return null
    }


    @Internal
    @TestOnly
    @JvmStatic
    @RequiresBackgroundThread
    fun getPythonCommunityPath(): Path {
      val pathFromUltimate = Path.of(PathManager.getHomePath(), "community/python")
      if (pathFromUltimate.exists()) {
        return pathFromUltimate
      }
      return Path.of(PathManager.getHomePath(), "python")
    }

    /**
     * Retrieves the absolute path of a helper file given its resource name.
     *
     * @param resourceName The name of the helper resource file (for example, `pydev` or `jupyter_debug`).
     * @return The absolute path of the helper file, or null if the file does not exist.
     */
    @Internal
    @JvmStatic
    @RequiresBackgroundThread
    fun findPathStringInHelpers(@NonNls resourceName: String): String = findPathInHelpers(resourceName)?.absolutePathString() ?: ""

    @Deprecated("Use {@link PythonHelpersLocator#findPathInHelpers}.", ReplaceWith("findPathInHelpers(resourceName)"))
    @JvmStatic
    fun getHelperPath(@NonNls resourceName: String): String = findPathStringInHelpers(resourceName)
  }

  @Internal
  fun getRoot(): Path? = getCommunityHelpersRootPath().normalize()

  private fun getCommunityHelpersRootPath(): Path {
    val property = System.getProperty(PROPERTY_HELPERS_LOCATION)
    if (property != null) {
      return Path.of(property)
    }
    else {
      return getHelpersRoot(COMMUNITY_HELPERS_MODULE_NAME, "/python/helpers").also {
        assertHelpersLayout(it)
      }
    }
  }

  @Internal
  @RequiresBackgroundThread
  fun getHelpersRoot(moduleName: String, relativePath: String): Path {
    return findRootByJarPath(
      jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator::class.java),
      moduleName = moduleName,
      relativePath = relativePath,
    )
  }

  @Internal
  fun findRootByJarPath(jarPath: String, moduleName: String, relativePath: String): Path {
    return if (PluginManagerCore.isRunningFromSources()) {
      Path.of(PathManager.getCommunityHomePath(), relativePath)
    }
    else {
      getPluginBaseDir(jarPath)?.let {
        Path.of(it.absolutePathString(), PathUtil.getFileName(relativePath))
      } ?: Path.of(Path.of(jarPath).parent.absolutePathString(), moduleName)
    }
  }

  private fun getPluginBaseDir(jarPath: String): Path? {
    if (jarPath.endsWith(".jar")) {
      val path = Path.of(jarPath)

      LOG.assertTrue(path.exists(), "$path to plugin base bir does not exists")
      return path.parent.parent
    }

    return null
  }

  @RequiresBackgroundThread
  private fun assertHelpersLayout(root: Path): Path {
    LOG.assertTrue(root.exists(), "Helpers root does not exist $root")
    listOf("generator3", "pycharm", "pycodestyle.py", "pydev", "syspath.py", "typeshed").forEach { child ->
      LOG.assertTrue(root.resolve(child).exists(), "No '$child' inside $root")
    }

    return root
  }
}

private class PythonHelpersLocatorDefault : PythonHelpersLocator