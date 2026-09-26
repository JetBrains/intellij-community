// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon
import org.jetbrains.annotations.ApiStatus

/**
 * Icons for Python packages in the tool window and the redesigned interpreter Settings tree
 * (PY-89840). SVGs live in the `intellij.python.packaging` module resources.
 */
@ApiStatus.Internal
object PyPackageIcons {
  @JvmField val Package: Icon = load("package.svg")
  @JvmField val PackagePipInstalled: Icon = load("packagePipInstalled.svg")
  @JvmField val PackageGray: Icon = load("packageGray.svg")
  @JvmField val Repository: Icon = load("repository.svg")
  @JvmField val RepositoryFailed: Icon = load("repositoryFailed.svg")
  @JvmField val Uninstall: Icon = load("uninstall.svg")
  @JvmField val ChangeVersion: Icon = load("changeVersion.svg")
  @JvmField val AddPackage: Icon = load("addPackage.svg")

  private fun load(name: String): Icon =
    IconLoader.getIcon("icons/com/jetbrains/python/packaging/$name", PyPackageIcons::class.java)
}
