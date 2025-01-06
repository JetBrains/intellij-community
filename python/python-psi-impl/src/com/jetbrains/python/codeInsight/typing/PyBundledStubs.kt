package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonHelpersLocator

object PyBundledStubs {
  private const val BUNDLED_STUBS_PATH = "bundled_stubs"

  fun isInside(file: VirtualFile): Boolean {
    return root?.let { return VfsUtil.isAncestor(it, file, false) } ?: false
  }

  /**
   * The actual bundled stubs directory.
   */
  val root: VirtualFile? by lazy {
    var helpersPath = PythonHelpersLocator.findPathStringInHelpers(BUNDLED_STUBS_PATH);
    if (helpersPath.isEmpty()) {
      return@lazy null
    }
    StandardFileSystems.local().findFileByPath(helpersPath)
  }

  /**
   * Find the directory containing .pyi stubs for the package [packageName] among the bundled stubs.
   *
   * [packageName] should match the name of the package on PyPI.
   */
  fun getStubRootForPackage(packageName: String): VirtualFile? {
    return root?.findChild(packageName)
  }
}
