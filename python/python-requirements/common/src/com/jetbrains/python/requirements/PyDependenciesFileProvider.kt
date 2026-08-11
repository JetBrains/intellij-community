// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for recognising Python dependency files. Each provider inspects the given
 * [VirtualFile] (typically by name and optionally by content) and returns its own
 * [PyDependenciesFile] implementation, or `null` if it doesn't recognise the file.
 *
 * Use [resolve] to walk all registered providers and obtain the first non-null match.
 */
@ApiStatus.Internal
interface PyDependenciesFileProvider {
  /**
   * Returns a [PyDependenciesFile] if this provider recognises [file]; otherwise `null`.
   * Implementations should be fast for the common rejection case (e.g. compare the file
   * name first) and may suspend for any heavier validation across file content.
   */
  suspend fun fromFile(file: VirtualFile): PyDependenciesFile?

  companion object {
    val EP_NAME: ExtensionPointName<PyDependenciesFileProvider> = ExtensionPointName.create("Pythonid.pyDependenciesFileProvider")

    /**
     * Walks the registered [PyDependenciesFileProvider]s in extension-point order and
     * returns the first non-null [PyDependenciesFile] produced by any of them, or `null`
     * if no provider recognises [file].
     */
    suspend fun resolve(file: VirtualFile): PyDependenciesFile? = EP_NAME.extensionList.firstNotNullOfOrNull { it.fromFile(file) }
  }
}
