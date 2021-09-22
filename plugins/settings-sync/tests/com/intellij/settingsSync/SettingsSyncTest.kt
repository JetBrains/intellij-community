package com.intellij.settingsSync

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.StateLoadPolicy
import com.intellij.configurationStore.getDefaultStoragePathSpec
import com.intellij.configurationStore.serializeStateInto
import com.intellij.ide.GeneralSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.createDirectories
import com.intellij.util.toByteArray
import com.intellij.util.xmlb.Constants
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val TIMEOUT_UNIT = TimeUnit.MINUTES

@RunWith(JUnit4::class)
internal class SettingsSyncTest {

  private val appRule = ApplicationRule()
  private val tempDirManager = TemporaryDirectory()
  private val disposableRule = DisposableRule()
  @Rule @JvmField val ruleChain: RuleChain = RuleChain.outerRule(tempDirManager).around(appRule).around(disposableRule)

  private lateinit var application: Application
  private lateinit var configDir: Path
  private lateinit var componentStore: TestComponentStore
  private lateinit var remoteCommunicator: TestRemoteCommunicator
  private lateinit var updateChecker: SettingsSyncUpdateChecker
  private lateinit var bridge: SettingsSyncBridge

  @Before
  fun setup() {
    application = ApplicationManager.getApplication() as ApplicationEx
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()
    componentStore = TestComponentStore(configDir)
    remoteCommunicator = TestRemoteCommunicator()
  }

  @After
  fun cleanup() {
    bridge.waitForAllExecuted(10, TimeUnit.SECONDS)
  }

  private fun initSettingsSync() {
    val settingsSyncStorage = configDir.resolve("settingsSync")
    val controls = SettingsSyncMain.init(application, disposableRule.disposable, settingsSyncStorage, configDir, componentStore, remoteCommunicator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
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

    UISettings.instance.initModifyAndSave {
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
    remoteCommunicator.updateResult = UpdateResult.Success(SettingsSnapshot(setOf(fileState)))

    updateChecker.updateFromServer()

    waitForSettingsToBeApplied(generalSettings)
    assertFalse(generalSettings.isSaveOnFrameDeactivation)
  }

  private fun performInOfflineMode(action: () -> Unit) {
    remoteCommunicator.offline = true
    val cdl = CountDownLatch(1)
    remoteCommunicator.startPushLatch = cdl
    action()
    assertTrue("Didn't await for the push request", cdl.await(5, TIMEOUT_UNIT))
  }

  @Test
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
    remoteCommunicator.updateResult = UpdateResult.Success(SettingsSnapshot(setOf(fileState)))
    remoteCommunicator.offline = false

    updateChecker.updateFromServer() // merge will happen here

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

  private fun waitForSettingsPush(assertSnapshot: (SettingsSnapshot) -> Unit) {
    val cdl = CountDownLatch(1)
    remoteCommunicator.pushedLatch = cdl
    assertTrue("Didn't await until changes are pushed", cdl.await(5, TIMEOUT_UNIT))

    val pushedSnap = remoteCommunicator.pushed
    assertNotNull("Changes were not pushed", pushedSnap)
    assertSnapshot(pushedSnap!!)
  }

  private fun <T : PersistentStateComponent<*>> T.init() : T {
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


  private fun assertSettingsPushed(build: SettingsSnapshotBuilder.() -> Unit) {
    waitForSettingsPush { pushedSnap ->
      pushedSnap.assertSettingsSnapshot {
        build()
      }
    }
  }

  private fun SettingsSnapshot.assertSettingsSnapshot(build: SettingsSnapshotBuilder.() -> Unit) {
    val settingsSnapshotBuilder = SettingsSnapshotBuilder()
    settingsSnapshotBuilder.build()
    val actualMap = this.fileStates.associate { it.file to String(it.content, UTF_8) }
    val expectedMap = settingsSnapshotBuilder.fileStates.associate { it.file to String(it.content, UTF_8) }
    assertEquals(expectedMap, actualMap)
  }

  private val <T> PersistentStateComponent<T>.name: String
    get() = (this::class.annotations.find { it is State } as? State)?.name!!

  private fun PersistentStateComponent<*>.serialize(): ByteArray {
    val compElement = Element("component")
    compElement.setAttribute(Constants.NAME, this.name)
    serializeStateInto(this, compElement)

    val appElement = Element("application")
    appElement.addContent(compElement)
    return appElement.toByteArray()
  }

  fun PersistentStateComponent<*>.toFileState() : FileState {
    val file = OPTIONS_DIRECTORY + "/" + getDefaultStoragePathSpec(this::class.java)
    val content = this.serialize()
    return FileState(file, content, content.size)
  }

  internal inner class SettingsSnapshotBuilder {
    val fileStates = mutableListOf<FileState>()

    fun fileState(function: () -> PersistentStateComponent<*>) {
      val component : PersistentStateComponent<*> = function()
      fileStates.add(component.toFileState())
    }
  }

  internal class TestRemoteCommunicator : SettingsSyncRemoteCommunicator {
    var offline: Boolean = false
    var updateNeeded: Boolean = false
    var updateResult: UpdateResult? = null
    var pushed: SettingsSnapshot? = null
    var startPushLatch: CountDownLatch? = null
    lateinit var pushedLatch: CountDownLatch

    override fun isUpdateNeeded(): Boolean {
      return updateNeeded
    }

    override fun receiveUpdates(): UpdateResult {
      return updateResult ?: UpdateResult.Error("Unexpectedly null update result")
    }

    override fun push(snapshot: SettingsSnapshot): SettingsSyncPushResult {
      startPushLatch?.countDown()
      if (offline) return SettingsSyncPushResult.Error("Offline")

      pushed = snapshot
      if (::pushedLatch.isInitialized) pushedLatch.countDown()
      return SettingsSyncPushResult.Success
    }
  }

  internal class TestComponentStore(configDir: Path): ApplicationStoreImpl() {
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