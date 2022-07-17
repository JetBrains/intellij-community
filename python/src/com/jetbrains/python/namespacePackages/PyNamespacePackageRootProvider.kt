// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.namespacePackages

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.actions.ContentEntryEditingAction
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.ui.JBColor
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.MultiMap
import com.jetbrains.python.PyBundle
import com.jetbrains.python.module.PyContentEntriesEditor
import com.jetbrains.python.module.PyRootTypeProvider
import java.awt.Color
import javax.swing.Icon
import javax.swing.JTree

class PyNamespacePackageRootProvider: PyRootTypeProvider() {
  private val myNamespacePackages = MultiMap<ContentEntry, VirtualFilePointer>()

  init {
    if (!Registry.`is`("python.explicit.namespace.packages")) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun reset(disposable: Disposable, editor: PyContentEntriesEditor, module: Module) {
    myNamespacePackages.clear()
    val namespacePackages = PyNamespacePackagesService.getInstance(module).namespacePackageFoldersVirtualFiles
    for (namespacePackage in namespacePackages) {
      val contentEntry = findContentEntryForFile(namespacePackage, editor) ?: continue
      val pointer = VirtualFilePointerManager.getInstance().create(namespacePackage, disposable, DUMMY_LISTENER)
      myNamespacePackages.putValue(contentEntry, pointer)
    }
  }

  override fun apply(module: Module) {
    val instance = PyNamespacePackagesService.getInstance(module)
    val currentNamespacePackages = getCurrentNamespacePackages()
    if (!Comparing.haveEqualElements(instance.namespacePackageFoldersVirtualFiles, currentNamespacePackages)) {
      instance.namespacePackageFoldersVirtualFiles = currentNamespacePackages
      PyNamespacePackagesStatisticsCollector.logApplyInNamespacePackageRootProvider()
    }
  }

  override fun isModified(module: Module): Boolean =
    !Comparing.haveEqualElements(PyNamespacePackagesService.getInstance(module).namespacePackageFoldersVirtualFiles,
                                 getCurrentNamespacePackages())

  override fun getRoots(): MultiMap<ContentEntry, VirtualFilePointer> = myNamespacePackages

  override fun getIcon(): Icon {
    return PlatformIcons.PACKAGE_ICON
  }

  override fun getName(): String {
    return PyBundle.message("python.namespace.packages.name")
  }

  override fun getDescription(): String {
    return PyBundle.message("python.namespace.packages.description")
  }

  override fun getRootsGroupColor(): Color {
    return EASTERN_BLUE
  }

  override fun createRootEntryEditingAction(tree: JTree?,
                                            disposable: Disposable?,
                                            editor: PyContentEntriesEditor?,
                                            model: ModifiableRootModel?): ContentEntryEditingAction {
    return RootEntryEditingAction(tree, disposable, editor, model)
  }

  private fun getCurrentNamespacePackages(): List<VirtualFile> = myNamespacePackages.values().mapNotNull { it.file }

  companion object {
    private val EASTERN_BLUE: Color = JBColor(0x29A5AD, 0x29A5AD)
  }
}