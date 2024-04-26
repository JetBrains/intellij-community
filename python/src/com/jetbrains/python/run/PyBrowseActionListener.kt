/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.extensions.withPythonFiles

open class PyBrowseActionListener
@JvmOverloads
constructor(private val configuration: AbstractPythonRunConfiguration<*>,
            chooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor().withPythonFiles())
  : TextBrowseFolderListener(chooserDescriptor, configuration.getProject()) {

  final override fun getInitialFile(): VirtualFile? =
    super.getInitialFile() ?: LocalFileSystem.getInstance().findFileByPath(configuration.getWorkingDirectorySafe())

}