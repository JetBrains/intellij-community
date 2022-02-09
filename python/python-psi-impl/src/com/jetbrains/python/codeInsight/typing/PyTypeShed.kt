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

  private val stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels = mapOf(
    // name to python versions when this name was introduced and removed
    "_bootlocale" to (LanguageLevel.PYTHON36 to LanguageLevel.PYTHON39),
    "_dummy_thread" to (LanguageLevel.PYTHON36 to LanguageLevel.PYTHON38),
    "_dummy_threading" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON38),
    "_py_abc" to (LanguageLevel.PYTHON37 to null),
    "binhex" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON310),
    "contextvars" to (LanguageLevel.PYTHON37 to null),
    "dataclasses" to (LanguageLevel.PYTHON37 to null),
    "distutils.command.bdist_msi" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON310),  // likely it is ignored now
    "dummy_threading" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON38),
    "formatter" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),
    "graphlib" to (LanguageLevel.PYTHON39 to null),
    "importlib.metadata" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "importlib.metadata._meta" to (LanguageLevel.PYTHON310 to null),  // likely it is ignored now
    "importlib.resources" to (LanguageLevel.PYTHON37 to null),  // likely it is ignored now
    "macpath" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON37),
    "macurl2path" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON36),
    "parser" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),
    "symbol" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),
    "unittest._log" to (LanguageLevel.PYTHON39 to null),  // likely it is ignored now
    "zoneinfo" to (LanguageLevel.PYTHON39 to null)
  )

  /**
   * Returns true if we allow to search typeshed for a stub for [name].
   */
  fun maySearchForStubInRoot(name: QualifiedName, root: VirtualFile, sdk: Sdk): Boolean {
    if (isInStandardLibrary(root)) {
      val head = name.firstComponent ?: return true
      val languageLevels = stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels[head] ?: return true
      val currentLanguageLevel = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk)
      return currentLanguageLevel.isAtLeast(languageLevels.first) && languageLevels.second.let {
        it == null || it.isAtLeast(currentLanguageLevel)
      }
    }
    if (isInThirdPartyLibraries(root)) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return true
      }
      val possiblePackage = name.firstComponent ?: return false
      val alternativePossiblePackages = PyPsiPackageUtil.PACKAGES_TOPLEVEL[possiblePackage] ?: emptyList()

      val packageManager = PyPackageManagers.getInstance().forSdk(sdk)
      val installedPackages = if (ApplicationManager.getApplication().isHeadlessEnvironment) {
        packageManager.refreshAndGetPackages(false)
      }
      else {
        packageManager.packages ?: return true
      }

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
    return findRootsForLanguageLevel(level)
  }

  /**
   * Returns the list of roots in typeshed for the specified Python language [level].
   */
  fun findRootsForLanguageLevel(level: LanguageLevel): List<VirtualFile> {
    val dir = directory ?: return emptyList()

    val common = sequenceOf(dir.findChild("stdlib"))
      .plus(dir.findFileByRelativePath("stubs")?.children ?: VirtualFile.EMPTY_ARRAY)
      .filterNotNull()
      .toList()

    return if (level.isPython2) common.flatMap { listOfNotNull(it.findChild("@python2"), it) } else common
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

  private val directoryPath: String?
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
  fun isInThirdPartyLibraries(file: VirtualFile): Boolean = "stubs" in file.path

  fun isInStandardLibrary(file: VirtualFile): Boolean = "stdlib" in file.path
}
