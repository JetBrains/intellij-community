/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType

/**
 * @author vlan
 */
object PyTypeShed {
  private val ONLY_SUPPORTED_PY2_MINOR = 7
  private val SUPPORTED_PY3_MINORS = 2..5
  // TODO: Add `typing` to the white list and fix the tests
  // TODO: Warn about unresolved `import typing` but still resolve it internally for type inference
  private val WHITE_LIST = setOf("six")

  /**
   * Returns true if we allow to search typeshed for a stub for [name].
   */
  fun maySearchForStubInRoot(name: QualifiedName, root: VirtualFile, sdk : Sdk): Boolean {
    val topLevelPackage = name.firstComponent ?: return false
    if (topLevelPackage !in WHITE_LIST) {
      return false
    }
    if (isInStandardLibrary(root)) {
      return true
    }
    if (isInThirdPartyLibraries(root)) {
      val pyPIPackage = PyPIPackageUtil.PACKAGES_TOPLEVEL[topLevelPackage] ?: topLevelPackage
      val packages = PyPackageManagers.getInstance().forSdk(sdk).packages ?: return true
      return PyPackageUtil.findPackage(packages, pyPIPackage) != null
    }
    return false
  }

  /**
   * Returns list of roots in typeshed for Python language level of [sdk].
   */
  fun findRootsForSdk(sdk: Sdk): List<VirtualFile> {
    val level = PythonSdkType.getLanguageLevelForSdk(sdk)
    val minor = when (level.major) {
      2 -> ONLY_SUPPORTED_PY2_MINOR
      3 -> Math.min(Math.max(level.minor, SUPPORTED_PY3_MINORS.start), SUPPORTED_PY3_MINORS.endInclusive)
      else -> return emptyList()
    }
    val dir = directory ?: return emptyList()
    val paths = listOf("stdlib/${level.major}.${minor}",
                       "stdlib/${level.major}",
                       "stdlib/2and3",
                       "third_party/${level.major}",
                       "third_party/2and3")
    return paths.asSequence()
        .map { dir.findFileByRelativePath(it) }
        .filterNotNull()
        .toList()
  }

  fun isInside(file: VirtualFile): Boolean {
    val dir = directory
    return dir != null && VfsUtilCore.isAncestor(dir, file, true)
  }

  val directory: VirtualFile? by lazy {
    val paths = listOf("${PathManager.getConfigPath()}/typeshed",
                       PythonHelpersLocator.getHelperPath("typeshed"))
    paths.asSequence()
        .map { StandardFileSystems.local().findFileByPath(it) }
        .filterNotNull()
        .firstOrNull()
  }

  fun isInThirdPartyLibraries(file: VirtualFile) = "third_party" in file.path

  private fun isInStandardLibrary(file: VirtualFile) = "stdlib" in file.path

  private val LanguageLevel.major: Int
    get() = this.version / 10

  private val LanguageLevel.minor: Int
    get() = this.version % 10
}
