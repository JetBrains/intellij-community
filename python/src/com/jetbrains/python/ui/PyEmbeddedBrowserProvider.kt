// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies the embedded browser to the Python plugin.
 *
 * The JCEF plugin is optional. The module `intellij.python.community.impl.jcef` registers the only implementation,
 * and it loads only when the JCEF plugin is present. The companion helpers fall back to Swing when no provider is available.
 */
@ApiStatus.Internal
interface PyEmbeddedBrowserProvider {
  /**
   * Returns true when the embedded browser can show pages in this IDE instance.
   */
  fun isSupported(): Boolean

  fun createHtmlPanel(project: Project): PyHtmlPanel

  companion object {
    private val EP: ExtensionPointName<PyEmbeddedBrowserProvider> = ExtensionPointName.create("Pythonid.embeddedBrowserProvider")

    /**
     * Returns true when a registered provider supports the embedded browser.
     */
    fun isSupported(): Boolean = EP.extensionList.any { it.isSupported() }

    /**
     * Creates an HTML panel. The panel uses the embedded browser when [isSupported] is true, else Swing.
     */
    fun createHtmlPanel(project: Project): PyHtmlPanel {
      val provider = EP.extensionList.firstOrNull { it.isSupported() }
      return provider?.createHtmlPanel(project) ?: PySwingHtmlPanel()
    }
  }
}
