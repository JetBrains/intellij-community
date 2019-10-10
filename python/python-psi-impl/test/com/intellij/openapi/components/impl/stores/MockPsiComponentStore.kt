package com.intellij.openapi.components.impl.stores

import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.util.messages.MessageBus

class MockPsiComponentStore: IComponentStore {
  override val storageManager: StateStorageManager
    get() = throw UnsupportedOperationException()

  override fun setPath(path: String) {
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
  }

  override fun initPersistencePlainComponent(component: Any, key: String) {
  }

  override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
  }

  override fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
  }

  override fun isReloadPossible(componentNames: Set<String>): Boolean = false

  override suspend fun save(forceSavingAllSettings: Boolean) {
  }

  override fun saveComponent(component: PersistentStateComponent<*>) {
  }
}