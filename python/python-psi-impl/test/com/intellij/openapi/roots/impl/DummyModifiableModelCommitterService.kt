package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.roots.ModifiableRootModel

class DummyModifiableModelCommitterService: ModifiableModelCommitterService {
  override fun multiCommit(rootModels: MutableCollection<out ModifiableRootModel>, moduleModel: ModifiableModuleModel) {

  }
}