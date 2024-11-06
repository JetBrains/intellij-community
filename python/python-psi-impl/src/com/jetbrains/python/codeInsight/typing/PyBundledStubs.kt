package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.QualifiedName
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.packaging.PyPackageManagers

object PyBundledStubs {
  private const val BUNDLED_STUBS_PATH = "bundled_stubs"

  /**
   * The actual bundled stubs directory.
   */
  private val BUNDLED_STUB_ROOT: VirtualFile? by lazy {
    var helpersPath = PythonHelpersLocator.findPathStringInHelpers(BUNDLED_STUBS_PATH);
    if (helpersPath.isEmpty()) {
      return@lazy null
    }
    StandardFileSystems.local().findFileByPath(helpersPath)
  }

  fun maySearchForStubInRoot(name: QualifiedName, root: VirtualFile, sdk: Sdk): Boolean {
    // TODO merge with PyTypeShed
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return true
    }
    val possiblePackage = name.firstComponent ?: return false
    val alternativePossiblePackage = PyPsiPackageUtil.moduleToPackageName(possiblePackage, default = "")

    val packageManager = PyPackageManagers.getInstance().forSdk(sdk)
    val installedPackages = if (ApplicationManager.getApplication().isHeadlessEnvironment && !PlatformUtils.isFleetBackend()) {
      packageManager.refreshAndGetPackages(false)
    }
    else {
      packageManager.packages ?: return true
    }

    return packageManager.parseRequirement(possiblePackage)?.match(installedPackages) != null ||
           PyPsiPackageUtil.findPackage(installedPackages, alternativePossiblePackage) != null
  }

  /**
   * Checks if the [file] is located inside the bundled stubs directory.
   */
  fun isBundledStubsDirectory(file: VirtualFile): Boolean {
    return file == BUNDLED_STUB_ROOT
  }

  fun getRoots(): Iterable<VirtualFile> {
    return listOfNotNull(BUNDLED_STUB_ROOT);
  }
}
