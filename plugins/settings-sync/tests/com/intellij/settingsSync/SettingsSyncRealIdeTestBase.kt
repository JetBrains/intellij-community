package com.intellij.settingsSync

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.StateLoadPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.lang.reflect.Constructor
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

internal abstract class SettingsSyncRealIdeTestBase : SettingsSyncTestBase() {
  protected lateinit var componentStore: TestComponentStore

  @Before
  fun setupComponentStore() {
    componentStore = TestComponentStore(configDir)
    application.replaceService(IComponentStore::class.java, componentStore, disposable)
  }

  @After
  fun resetComponentStatesToDefault() {
    componentStore.resetComponents()
  }

  protected fun initSettingsSync(initMode: SettingsSyncBridge.InitMode = SettingsSyncBridge.InitMode.JustInit) {
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore, configDir, enabledCondition = { true })
    val controls = SettingsSyncMain.init(disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(initMode)
  }

  protected fun waitForSettingsToBeApplied(vararg componentsToReinit: PersistentStateComponent<*>, execution: () -> Unit) {
    val cdl = CountDownLatch(1)
    componentStore.reinitLatch = cdl

    execution()

    Assert.assertTrue("Didn't await until new settings are applied", cdl.wait())

    val reinitedComponents = componentStore.reinitedComponents
    for (componentToReinit in componentsToReinit) {
      val componentName = componentToReinit.name
      Assert.assertTrue("Reinitialized components don't contain $componentName among those: $reinitedComponents",
                        reinitedComponents.contains(componentName))
    }
  }

  protected fun <T : PersistentStateComponent<*>> T.init(): T {
    componentStore.initComponent(this, null, null)
    val defaultConstructor: Constructor<T> = this::class.java.declaredConstructors.find { it.parameterCount == 0 } as Constructor<T>
    val componentInstance: T = defaultConstructor.newInstance()
    componentStore.componentsAndDefaultStates[this] = componentInstance.state!!
    return this
  }

  protected fun <State, Component : PersistentStateComponent<State>> Component.initModifyAndSave(modifier: State.() -> Unit): Component {
    this.init()
    this.state!!.modifier()
    runBlocking {
      componentStore.save()
    }
    return this
  }

  protected fun <State, Component : PersistentStateComponent<State>> Component.withState(stateApplier: State.() -> Unit): Component {
    stateApplier(this.state!!)
    return this
  }

  class TestComponentStore(configDir: Path) : ApplicationStoreImpl(ApplicationManager.getApplication()) {
    override val loadPolicy: StateLoadPolicy
      get() = StateLoadPolicy.LOAD

    val componentsAndDefaultStates = mutableMapOf<PersistentStateComponent<*>, Any>()
    val reinitedComponents = mutableListOf<String>()
    lateinit var reinitLatch: CountDownLatch

    init {
      setPath(configDir)
    }

    override fun reinitComponents(componentNames: Set<String>,
                                  changedStorages: Set<StateStorage>,
                                  notReloadableComponents: Collection<String>) {
      super.reinitComponents(componentNames, changedStorages, notReloadableComponents)

      reinitedComponents.addAll(componentNames)
      if (::reinitLatch.isInitialized) reinitLatch.countDown()
    }

    fun resetComponents() {
      for ((component, defaultState) in componentsAndDefaultStates) {
        val c = component as PersistentStateComponent<Any>
        c.loadState(defaultState)
      }
    }

  }
}