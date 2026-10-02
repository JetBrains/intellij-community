// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ResourceUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * Python package utility methods with no dependencies on the Python runtime.
 *
 * @see PyPackageUtil for other package utility methods, including run-time dependent parts.
 */
@ApiStatus.Internal
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

  /**
   * Whether the letter case of [module] can be trusted to match the module that the package [moduleToPackageName]
   * maps it to provides.
   *
   * PyPI project names are case-insensitive, but module names are not: `Starlette` maps to the project `starlette`,
   * whose module is `starlette`. A module name with capitals is trusted only when the alias table lists it.
   */
  fun isModuleNameCaseTrusted(module: String): Boolean {
    return module in PACKAGES_TOPLEVEL || module == module.lowercase()
  }

  private fun loadPackageAliases(): Map<String, String> {
    return buildMap {
      try {
        val resourceAsStream = requireNotNull(PyPsiPackageUtil::class.java.getClassLoader().getResourceAsStream("tools/packages"))
        { "tool/packages is missed in distribution " }
        for (line in ResourceUtil.loadText(resourceAsStream).lines()) {
          if (line.isBlank()) continue
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
