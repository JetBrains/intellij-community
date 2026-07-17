// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons for Python packages in the tool window.
 * SVGs are stored in the `intellij.python.packaging` module resources.
 */
internal object PyPackageIcons {
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
