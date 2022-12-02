// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.workspaceModel.storage.bridgeEntities.*

interface ModuleModelProxy {
  fun disposeModule(module: Module)
  fun findModuleByName(name: String): Module?
  fun newModule(path: String, moduleTypeId: String): Module
  fun setModuleGroupPath(module: Module, groupPath: Array<String>?)
  @Throws(ModuleWithNameAlreadyExists::class)
  fun renameModule(module: Module, moduleName: String)
  val modules: Array<Module>
}

class ModuleModelProxyWrapper(val delegate: ModifiableModuleModel) : ModuleModelProxy {
  override fun disposeModule(module: Module) {
    delegate.disposeModule(module)
  }

  override fun findModuleByName(name: String): Module? {
    return delegate.findModuleByName(name)
  }

  override fun newModule(path: String, moduleTypeId: String): Module {
    return delegate.newModule(path, moduleTypeId)
  }

  override fun setModuleGroupPath(module: Module, groupPath: Array<String>?) {
    delegate.setModuleGroupPath(module, groupPath)
  }

  override fun renameModule(module: Module, moduleName: String) {
    delegate.renameModule(module, moduleName)
  }

  override val modules: Array<Module>
    get() = delegate.modules

}

