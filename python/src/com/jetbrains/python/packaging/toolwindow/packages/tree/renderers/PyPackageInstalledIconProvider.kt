// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree.renderers

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.packaging.common.PythonPackage
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Lets installed-from–specific icons live next to the code that defines them instead of being
 * hardcoded in [PyPackageTreeCellRenderer]. The conda integration ships its own provider that
 * returns the Anaconda icon for packages installed via conda (not pip); other ecosystems can plug
 * their own without touching the generic renderer.
 *
 * Providers are queried in registration order; the first non-null icon wins. Return `null` to
 * defer to the next provider (or the renderer's default).
 */
@ApiStatus.Internal
interface PyPackageInstalledIconProvider {
  fun iconFor(pkg: PythonPackage): Icon?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<PyPackageInstalledIconProvider> =
      ExtensionPointName.create("Pythonid.packageInstalledIconProvider")
  }
}
