package com.intellij.settingsSync

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.StateLoadPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.settingsSync.GitSettingsLog.Companion
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.intellij.util.progress.sleepCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.lang.reflect.Constructor
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

internal abstract class SettingsSyncRealIdeTestBase : SettingsSyncTestBase() {
  protected lateinit var componentStore: TestComponentStore

  @BeforeEach
  fun setupComponentStore() {
    componentStore = TestComponentStore(configDir)
    application.replaceService(IComponentStore::class.java, componentStore, disposable)
    //warm up

    application.processAllImplementationClasses { componentClass, plugin ->
      // do nothing
    }
  }

  @AfterEach
  fun resetComponentStatesToDefault() {
    componentStore.resetComponents()
  }

  protected suspend fun initSettingsSync(initMode: SettingsSyncBridge.InitMode = SettingsSyncBridge.InitMode.JustInit, crossIdeSync: Boolean = false) {
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().state.crossIdeSyncEnabled = crossIdeSync;
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore, configDir, enabledCondition = { true })
    val controls = SettingsSyncMainImpl.init(currentThreadCoroutineScope(), disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(initMode)
    timeoutRunBlocking(5.seconds) {
      while (!bridge.isInitialized) {
        yield()
      }
    }
  }

  protected fun waitForSettingsToBeApplied(vararg componentsToReinit: PersistentStateComponent<*>, execution: () -> Unit) {
    val cdl = CountDownLatch(1)
    componentStore.reinitLatch = cdl

    execution()
    bridge.waitForAllExecuted()

    assertTrue(cdl.wait(), "Didn't await until new settings are applied")

    val reinitedComponents = componentStore.reinitedComponents
    for (componentToReinit in componentsToReinit) {
      val componentName = componentToReinit.name
      assertTrue(reinitedComponents.contains(componentName), "Reinitialized components don't contain $componentName among those: $reinitedComponents")
    }
  }

  protected fun <T : PersistentStateComponent<*>> T.init(): T {
    componentStore.initComponent(component = this, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
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

  companion object {
    @JvmStatic
    @BeforeAll
    fun warmUp(): Unit {
      val tempDir = createTempDirectory("gitWarmup-${System.currentTimeMillis()}", "beforeAll")
      val parentDisposable = Disposer.newDisposable()
      val gitSettingsLog = GitSettingsLog(
        tempDir.resolve("storage").toPath(),
        tempDir.resolve("config").toPath(),
        parentDisposable,
        { null },
        initialSnapshotProvider = {
          SettingsSnapshot(
            SettingsSnapshot.MetaInfo(Instant.now(), null, true),
            emptySet(), null, emptyMap(), emptySet()
          )

        }
      )
      gitSettingsLog.initialize()
      Disposer.dispose(parentDisposable)
    }
  }
}