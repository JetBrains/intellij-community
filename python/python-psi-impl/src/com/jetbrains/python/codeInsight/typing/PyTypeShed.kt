/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.psi.LanguageLevel
import java.io.File

/**
 * Utilities for managing the local copy of the typeshed repository.
 *
 * The original Git repo is located [here](https://github.com/JetBrains/typeshed).
 *
 * @author vlan
 */
object PyTypeShed {
  private const val ONLY_SUPPORTED_PY2_MINOR = 7
  private val SUPPORTED_PY3_MINORS = LanguageLevel.SUPPORTED_LEVELS.filter { it.isPy3K }.map { it.minor }

  /**
   * Returns true if we allow to search typeshed for a stub for [name].
   */
  fun maySearchForStubInRoot(name: QualifiedName, root: VirtualFile, sdk : Sdk): Boolean {
    if (isInStandardLibrary(root)) {
        return true
    }
    if (isInThirdPartyLibraries(root)) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return true
      }
      val possiblePackage = name.firstComponent ?: return false
      val alternativePossiblePackages = PyPsiPackageUtil.PACKAGES_TOPLEVEL[possiblePackage] ?: emptyList()

      val packageManager = PyPackageManagers.getInstance().forSdk(sdk)
      val installedPackages = packageManager.packages ?: return true

      return packageManager.parseRequirement(possiblePackage)?.match(installedPackages) != null ||
             alternativePossiblePackages.any { PyPsiPackageUtil.findPackage(installedPackages, it) != null }
    }
    return false
  }

  /**
   * Returns the list of roots in typeshed for the Python language level of [sdk].
   */
  fun findRootsForSdk(sdk: Sdk): List<VirtualFile> {
    val level = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk)
    val dir = directory ?: return emptyList()
    return findRootsForLanguageLevel(level)
        .asSequence()
        .map { dir.findFileByRelativePath(it) }
        .filterNotNull()
        .toList()
  }

  /**
   * Returns the list of roots in typeshed for the specified Python language [level].
   */
  fun findRootsForLanguageLevel(level: LanguageLevel): List<String> {
    val minors = when (level.major) {
      2 -> listOf(ONLY_SUPPORTED_PY2_MINOR)
      3 -> SUPPORTED_PY3_MINORS.reversed().filter { it <= level.minor }
      else -> return emptyList()
    }
    return minors.map { "stdlib/${level.major}.$it" } +
           listOf("stdlib/${level.major}",
                  "stdlib/2and3",
                  "third_party/${level.major}",
                  "third_party/2and3")
  }

  /**
   * Checks if the [file] is located inside the typeshed directory.
   */
  fun isInside(file: VirtualFile): Boolean {
    val dir = directory
    return dir != null && VfsUtilCore.isAncestor(dir, file, true)
  }

  /**
   * The actual typeshed directory.
   */
  val directory: VirtualFile? by lazy {
    val path = directoryPath ?: return@lazy null
    StandardFileSystems.local().findFileByPath(path)
  }

  val directoryPath: String?
    get() {
      val paths = listOf("${PathManager.getConfigPath()}/typeshed",
                         "${PathManager.getConfigPath()}/../typeshed",
                         PythonHelpersLocator.getHelperPath("typeshed"))
      return paths.asSequence()
          .filter { File(it).exists() }
          .firstOrNull()
    }

  /**
   * A shallow check for a [file] being located inside the typeshed third-party stubs.
   */
  fun isInThirdPartyLibraries(file: VirtualFile): Boolean = "third_party" in file.path

  fun isInStandardLibrary(file: VirtualFile): Boolean = "stdlib" in file.path

  private val LanguageLevel.major: Int
    get() = this.version / 10

  private val LanguageLevel.minor: Int
    get() = this.version % 10
}
