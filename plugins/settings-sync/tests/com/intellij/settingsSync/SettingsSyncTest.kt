package com.intellij.settingsSync

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.StateLoadPolicy
import com.intellij.ide.GeneralSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.testFramework.replaceService
import com.intellij.util.toBufferExposingByteArray
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch

@RunWith(JUnit4::class)
internal class SettingsSyncTest : SettingsSyncTestBase() {
  private lateinit var componentStore: TestComponentStore

  @Before
  fun setupComponentStore() {
    componentStore = TestComponentStore(configDir)
    application.replaceService(IComponentStore::class.java, componentStore, disposable)
  }

  private fun initSettingsSync() {
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore, configDir, enabledCondition = { true })
    val controls = SettingsSyncMain.init(application, disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(SettingsSyncBridge.InitMode.JustInit)
  }

  @Test
  fun `settings are pushed`() {
    initSettingsSync()

    GeneralSettings.getInstance().initModifyAndSave {
      isSaveOnFrameDeactivation = false
    }

    assertSettingsPushed {
      fileState {
        GeneralSettings().withState {
          isSaveOnFrameDeactivation = false
        }
      }
    }
  }

  @Test
  fun `scheme changes are logged`() {
    initSettingsSync()

    val keymap = KeymapImpl()
    val name = "SettingsSyncTestKeyMap"
    keymap.name = name

    KeymapManagerImpl().schemeManager.addScheme(keymap, false)

    runBlocking { componentStore.save() }

    assertSettingsPushed {
      fileState("keymaps/$name.xml", String(keymap.writeScheme().toBufferExposingByteArray().toByteArray(), Charset.defaultCharset()))
    }
  }

  @Test
  fun `quickly modified settings are pushed together`() {
    initSettingsSync()

    bridge.suspendEventProcessing()
    GeneralSettings.getInstance().initModifyAndSave {
      isSaveOnFrameDeactivation = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }
    bridge.resumeEventProcessing()

    assertSettingsPushed {
      fileState {
        GeneralSettings().withState {
          isSaveOnFrameDeactivation = false
        }
      }
      fileState {
        EditorSettingsExternalizable().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
  }

  @Test
  fun `existing settings are copied on initialization`() {
    GeneralSettings.getInstance().initModifyAndSave {
      isSaveOnFrameDeactivation = false
    }

    initSettingsSync()

    UISettings.getInstance().initModifyAndSave {
      recentFilesLimit = 1000
    }

    assertSettingsPushed {
      fileState {
        GeneralSettings().withState {
          isSaveOnFrameDeactivation = false
        }
      }
      fileState {
        UISettings().withState {
          recentFilesLimit = 1000
        }
      }
    }
  }

  @Test
  fun `settings from server are applied`() {
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync()

    val fileState = GeneralSettings().apply {
      isSaveOnFrameDeactivation = false
    }.toFileState()
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()), setOf(fileState)))

    updateChecker.scheduleUpdateFromServer()

    waitForSettingsToBeApplied(generalSettings)
    assertFalse(generalSettings.isSaveOnFrameDeactivation)
  }

  private fun performInOfflineMode(action: () -> Unit) {
    //remoteCommunicator.offline = true
    //val cdl = CountDownLatch(1)
    //remoteCommunicator.startPushLatch = cdl
    //action()
    //assertTrue("Didn't await for the push request", cdl.await(5, TIMEOUT_UNIT))
  }

  // temporarily disabled: the failure needs to be investigated
  //@Test
  fun `local and remote changes in different files are both applied`() {
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync()

    // prepare local commit but don't allow it to be pushed
    performInOfflineMode {
      EditorSettingsExternalizable.getInstance().initModifyAndSave {
        SHOW_INTENTION_BULB = false
      }
    }
    // at this point there is an unpushed local commit

    // prepare a remote commit and go online
    val fileState = GeneralSettings().apply {
      isSaveOnFrameDeactivation = false
    }.toFileState()
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()), setOf(fileState)))
    //remoteCommunicator.offline = false

    updateChecker.scheduleUpdateFromServer() // merge will happen here

    assertSettingsPushed {
      fileState {
        GeneralSettings().withState {
          isSaveOnFrameDeactivation = false
        }
      }
      fileState {
        EditorSettingsExternalizable().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
    waitForSettingsToBeApplied(generalSettings, EditorSettingsExternalizable.getInstance())
    assertFalse(generalSettings.isSaveOnFrameDeactivation)
    assertFalse(EditorSettingsExternalizable.getInstance().isShowIntentionBulb)
  }

  //@Test
  fun `only changed components should be reloaded`() {
    TODO()
  }

  private fun waitForSettingsToBeApplied(vararg componentsToReinit: PersistentStateComponent<*>) {
    val cdl = CountDownLatch(1)
    componentStore.reinitLatch = cdl
    assertTrue("Didn't await until new settings are applied", cdl.await(5, TIMEOUT_UNIT))

    val reinitedComponents = componentStore.reinitedComponents
    for (componentToReinit in componentsToReinit) {
      val componentName = componentToReinit.name
      assertTrue("Reinitialized components don't contain $componentName among those: $reinitedComponents",
                 reinitedComponents.contains(componentName))
    }
  }

  private fun <T : PersistentStateComponent<*>> T.init(): T {
    componentStore.initComponent(this, null, null)
    return this
  }

  private fun <State, Component : PersistentStateComponent<State>> Component.initModifyAndSave(modifier: State.() -> Unit): Component {
    this.init()
    this.state!!.modifier()
    runBlocking {
      componentStore.save()
    }
    return this
  }

  private fun <State, Component : PersistentStateComponent<State>> Component.withState(stateApplier: State.() -> Unit): Component {
    stateApplier(this.state!!)
    return this
  }


  private class TestComponentStore(configDir: Path) : ApplicationStoreImpl() {
    override val loadPolicy: StateLoadPolicy
      get() = StateLoadPolicy.LOAD

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
  }
}