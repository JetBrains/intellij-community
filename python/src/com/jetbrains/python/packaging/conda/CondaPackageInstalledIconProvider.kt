// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.python.community.impl.conda.icons.PythonCommunityImplCondaIcons
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageInstalledIconProvider
import javax.swing.Icon

/**
 * Surfaces the Anaconda icon in the package tree for packages that were installed via conda
 * rather than pip. Replaces the previous hardcoded `is CondaPackage` check inside the renderer
 * — the renderer now queries [PyPackageInstalledIconProvider.EP_NAME] without knowing about
 * conda at all.
 */
internal class CondaPackageInstalledIconProvider : PyPackageInstalledIconProvider {
  override fun iconFor(pkg: PythonPackage): Icon? {
    return if (pkg is CondaPackage && !pkg.installedWithPip) PythonCommunityImplCondaIcons.Anaconda else null
  }
}
