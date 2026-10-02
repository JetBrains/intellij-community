// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.community.helpersLocator.PythonBundledPyiStubsLocator
import kotlin.io.path.absolutePathString

object PyBundledStubs {
  private const val THIRD_PARTY_STUBS_DIR = "third_party"

  fun isInside(file: VirtualFile): Boolean {
    return root?.let { return VfsUtil.isAncestor(it, file, false) } ?: false
  }

  /**
   * The actual bundled stubs directory.
   */
  val root: VirtualFile? by lazy {
    val stubsPath = PythonBundledPyiStubsLocator.findPath(THIRD_PARTY_STUBS_DIR)
    StandardFileSystems.local().findFileByPath(stubsPath.absolutePathString())
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
