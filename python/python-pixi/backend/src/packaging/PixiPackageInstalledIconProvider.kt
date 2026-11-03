// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.common.icons.PythonPixiCommonIcons
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageInstalledIconProvider
import javax.swing.Icon

internal class PixiPackageInstalledIconProvider : PyPackageInstalledIconProvider {
  override fun iconFor(pkg: PythonPackage): Icon? =
    PythonPixiCommonIcons.Pixi.takeIf { pkg.defaultRepository is PixiNativeRepository }
}
