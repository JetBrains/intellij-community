// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

open class PyBrowseActionListener @JvmOverloads constructor(
  private val configuration: AbstractPythonRunConfiguration<*>,
  chooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor().withExtensionFilter("py")
) : TextBrowseFolderListener(chooserDescriptor, configuration.project) {
  final override fun getInitialFile(): VirtualFile? =
    super.getInitialFile() ?: LocalFileSystem.getInstance().findFileByPath(configuration.getWorkingDirectorySafe())
}
