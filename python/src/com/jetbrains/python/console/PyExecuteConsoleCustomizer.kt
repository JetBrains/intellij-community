// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.run.PythonRunConfiguration
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyExecuteConsoleCustomizer {
  companion object {
    private val EP_NAME: ExtensionPointName<PyExecuteConsoleCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.console.executeCustomizer")

    val instance: PyExecuteConsoleCustomizer
      get() = EP_NAME.extensionList.first()
  }

  /**
   * Return true if `virtualFile` supports execution in custom run descriptor. This descriptor will be used for executing the whole file
   * or a code fragment from it
   */
  @JvmDefault
  fun isCustomDescriptorSupported(virtualFile: VirtualFile): Boolean = false

  /**
   * Return type of a custom run descriptor, which will be used for executing virtualFile or a code fragment from it
   */
  @JvmDefault
  fun getCustomDescriptorType(virtualFile: VirtualFile): DescriptorType? = null

  /**
   * Return existing run descriptor, if a file's custom descriptor type is `DescriptorType.EXISTING`
   */
  @JvmDefault
  fun getExistingDescriptor(virtualFile: VirtualFile): RunContentDescriptor? = null

  /**
   * Update custom descriptor value and type for `virtualFile`
   */
  @JvmDefault
  fun updateDescriptor(virtualFile: VirtualFile, type: DescriptorType, descriptor: RunContentDescriptor?) {}

  /**
   * Notify about new name set for custom run descriptor
   */
  @JvmDefault
  fun descriptorNameUpdated(descriptor: RunContentDescriptor, newName: String) {}

  /**
   * Return run descriptor name
   */
  @JvmDefault
  fun getDescriptorName(descriptor: RunContentDescriptor): String = descriptor.displayName

  /**
   * Return a run configuration created from the context
   */
  @JvmDefault
  fun getContextConfig(dataContext: DataContext): PythonRunConfiguration? = null
}

enum class DescriptorType {
  NEW, EXISTING
}