package com.jetbrains.python

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ResourceUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageName
import java.io.IOException

/**
 * Python package utility methods with no dependencies on the Python runtime.
 *
 * @see PyPackageUtil for other package utility methods, including run-time dependent parts.
 */
object PyPsiPackageUtil {
  private val LOG = Logger.getInstance(PyPsiPackageUtil::class.java)

  /**
   * Contains mapping "importable top-level package" -> "package names on PyPI".
   */
  private val PACKAGES_TOPLEVEL: Map<String, String> = loadPackageAliases()

  @JvmStatic
  fun findPackage(packages: List<PyPackage>, name: String): PyPackage? {
    if (name.isEmpty()) return null
    for (pkg in packages) {
      if (name.equals(pkg.name, ignoreCase = true)) {
        return pkg
      }
    }
    return null
  }

  fun moduleToPackageName(module: String, default: String = module): String {
    return PyPackageName.normalizePackageName(PACKAGES_TOPLEVEL.getOrDefault(module, default))
  }

  private fun loadPackageAliases(): Map<String, String> {
    return buildMap {
      try {
        val resourceAsStream = requireNotNull(PyPsiPackageUtil::class.java.getClassLoader().getResourceAsStream("tools/packages"))
                                             { "tool/packages is missed in distribution "}
        for (line in ResourceUtil.loadText(resourceAsStream).lines()) {
          val split = line.trim().split(" ")
          check(split.size == 2) { "Each line should contain exactly two names: $line" }
          put(split[0], split[1])
        }
      }
      catch (e: IOException) {
        LOG.error("Cannot find \"packages\". " + e.message)
      }
    }
  }
}
