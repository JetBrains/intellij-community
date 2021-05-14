// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.nio.file.Path

class IdeModifiableModelsWrapper(val delegate: IdeModifiableModelsProvider, val moduleModel: ModuleModelProxy)
  : IdeModifiableModelsProvider by delegate {
  override fun getModifiableModuleModel(): ModifiableModuleModel {
    return ModifiableModuleModelWrapper(moduleModel);
  }
}

class ModifiableModuleModelWrapper(val moduleModel: ModuleModelProxy) : ModifiableModuleModel {
  override fun getModules(): Array<Module> = moduleModel.modules

  override fun newModule(filePath: String, moduleTypeId: String): Module = moduleModel.newModule(filePath, moduleTypeId)

  override fun newModule(filePath: String, moduleTypeId: String, options: MutableMap<String, String>?): Module {
    TODO("Not yet implemented")
  }

  override fun loadModule(filePath: String): Module {
    TODO("Not yet implemented")
  }

  override fun loadModule(file: Path): Module {
    TODO("Not yet implemented")
  }

  override fun disposeModule(module: Module) = moduleModel.disposeModule(module)

  override fun findModuleByName(name: String): Module? = moduleModel.findModuleByName(name)

  override fun dispose() {
    TODO("Not yet implemented")
  }

  override fun isChanged(): Boolean {
    TODO("Not yet implemented")
  }

  override fun commit() {
    TODO("Not yet implemented")
  }

  override fun renameModule(module: Module, newName: String) {
    TODO("Not yet implemented")
  }

  override fun getModuleToBeRenamed(newName: String): Module? {
    TODO("Not yet implemented")
  }

  override fun getNewName(module: Module): String? {
    TODO("Not yet implemented")
  }

  override fun getActualName(module: Module): String {
    TODO("Not yet implemented")
  }

  override fun getModuleGroupPath(module: Module): Array<String>? {
    TODO("Not yet implemented")
  }

  override fun hasModuleGroups(): Boolean {
    TODO("Not yet implemented")
  }

  override fun setModuleGroupPath(module: Module, groupPath: Array<String>?) {
    moduleModel.setModuleGroupPath(module, groupPath);
  }

  override fun getProject(): Project {
    TODO("Not yet implemented")
  }

}