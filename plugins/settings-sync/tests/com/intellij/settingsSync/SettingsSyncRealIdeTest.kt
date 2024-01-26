package com.intellij.settingsSync

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.ide.GeneralSettings
import com.intellij.ide.ui.UISettings
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.notification.NotificationService
import com.intellij.settingsSync.notification.NotificationServiceImpl
import com.intellij.testFramework.replaceService
import com.intellij.util.toByteArray
import com.intellij.util.xmlb.annotations.Attribute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.Mockito.verify
import java.nio.charset.Charset
import java.time.Instant
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

@RunWith(JUnit4::class)
internal class SettingsSyncRealIdeTest : SettingsSyncRealIdeTestBase() {

  @Test
  fun `settings are pushed`() {
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    executeAndWaitUntilPushed {
      GeneralSettings.getInstance().initModifyAndSave {
        autoSaveFiles = false
      }
    }

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
    }
  }

  @Test
  fun `scheme changes are logged`() {
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val keymap = createKeymap()
    executeAndWaitUntilPushed {
      saveComponentStore()
    }

    assertServerSnapshot {
      fileState("keymaps/${keymap.name}.xml", String(keymap.writeScheme().toByteArray(), Charset.defaultCharset()))
    }
  }

  private fun createKeymap(): KeymapImpl {
    val keymap = KeymapImpl()
    keymap.name = "SettingsSyncTestKeyMap"
    val keymapSchemeManager = KeymapManagerImpl().schemeManager
    keymapSchemeManager.addScheme(keymap, false)
    Disposer.register(disposable, Disposable {
      keymapSchemeManager.removeScheme(keymap)
    })
    return keymap
  }

  private fun saveComponentStore() {
    runBlocking { componentStore.save() }
  }

  @Test
  fun `quickly modified settings are pushed together`() {
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    bridge.suspendEventProcessing()
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }

    val pushedSnapshot = executeAndWaitUntilPushed {
      bridge.resumeEventProcessing()
    }

    pushedSnapshot.assertSettingsSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
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
      autoSaveFiles = false
    }

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    executeAndWaitUntilPushed {
      UISettings.getInstance().initModifyAndSave {
        recentFilesLimit = 1000
      }
    }

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        UISettings().withState {
          recentFilesLimit = 1000
        }
      }
    }
  }

  @Test fun `disabled categories should be ignored when copying settings on initialization`() {
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }
    //AppEditorFontOptions.getInstance().initModifyAndSave {
    //  FONT_SIZE = FontPreferences.DEFAULT_FONT_SIZE - 5
    //}
    val keymap = createKeymap()
    saveComponentStore()

    val os = getPerOsSettingsStorageFolderName()
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, false)
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.SYSTEM, false)
    //SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID,  false)

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    assertTrue("Settings from enabled category was not copied", (settingsSyncStorage / "options" / "editor.xml").exists())
    assertFalse("Settings from disabled category was copied", (settingsSyncStorage / "options" / "ide.general.xml").exists())
    //assertFalse("Settings from disabled subcategory was copied", (settingsSyncStorage / "options" / "editor-font.xml").exists())
    assertFalse("Settings from disabled category was copied", (settingsSyncStorage / "options" / os / "keymap.xml").exists())
    assertFalse("Schema from disabled category was copied", (settingsSyncStorage / "keymaps" / "${keymap.name}.xml").exists())
    remoteCommunicator.getVersionOnServer()!!.assertSettingsSnapshot {
      fileState {
        EditorSettingsExternalizable().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
  }

  @Test
  fun `settings from server are applied`() {
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val fileState = GeneralSettings().apply {
      isSaveOnFrameDeactivation = false
    }.toFileState()
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                                            setOf(fileState), null, emptyMap(), emptySet()))

    waitForSettingsToBeApplied(generalSettings) {
      fireSettingsChanged()
    }
    assertFalse(generalSettings.isSaveOnFrameDeactivation)
  }

  @Test
  fun `enabling category should copy existing settings from that category`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, isEnabled = false)
    GeneralSettings.getInstance().initModifyAndSave {
      autoSaveFiles = false
    }
    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      SHOW_INTENTION_BULB = false
    }
    val editorXmlContent = (configDir / "options" / "editor.xml").readText()
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)
    assertFalse("editor.xml should not be synced if the Code category is disabled",
                (settingsSyncStorage / "options" / "editor.xml").exists())
    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
    }

    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, isEnabled = true)
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.LogCurrentSettings)
    bridge.waitForAllExecuted()

    assertTrue("editor.xml should be synced after enabling the Code category",
               (settingsSyncStorage / "options" / "editor.xml").exists())
    assertFileWithContent(editorXmlContent, (settingsSyncStorage / "options" / "editor.xml"))
    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        EditorSettingsExternalizable.getInstance().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
  }

  @Test
  fun `exportable non-roamable settings should not be synced`() {
    testVariousComponentsShouldBeSyncedOrNot(ExportableNonRoamable(), expectedToBeSynced = false)
  }

  @Test
  fun `roamable settings should be synced`() {
    testVariousComponentsShouldBeSyncedOrNot(Roamable(), expectedToBeSynced = true)
  }

  private fun testVariousComponentsShouldBeSyncedOrNot(component: BaseComponent, expectedToBeSynced: Boolean) {
    component.aState.foo = "bar"
    runBlocking {
      application.componentStore.saveComponent(component)
    }
    application.registerComponentImplementation(component.javaClass, component.javaClass, false)

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val state = component::class.annotations.find { it is State } as State
    val file = state.storages.first().value

    val fileExists = settingsSyncStorage.resolve("options").resolve(file).exists()
    val assertMessage = "File $file of ${component::class.simpleName} should ${if (!expectedToBeSynced) "not " else ""}exist"
    if (expectedToBeSynced) {
      assertTrue(assertMessage, fileExists)
    }
    else {
      assertFalse(assertMessage, fileExists)
    }
  }

  private data class AState(@Attribute var foo: String = "")

  @State(name = "SettingsSyncTestExportableNonRoamable",
         storages = [Storage("settings-sync-test.exportable-non-roamable.xml", roamingType = RoamingType.DISABLED, exportable = true)])
  private class ExportableNonRoamable: BaseComponent()

  @State(name = "SettingsSyncTestRoamable",
         storages = [Storage("settings-sync-test.roamable.xml", roamingType = RoamingType.DEFAULT)],
         category = SettingsCategory.UI)
  private class Roamable: BaseComponent()

  private open class BaseComponent : PersistentStateComponent<AState> {
    var aState = AState()

    override fun getState() = aState

    override fun loadState(state: AState) {
      this.aState = state
    }
  }

  private fun performInOfflineMode(action: () -> Unit) {
    //remoteCommunicator.offline = true
    //val cdl = CountDownLatch(1)
    //remoteCommunicator.startPushLatch = cdl
    //action()
    //assertTrue("Didn't await for the push request", cdl.await(5, TIMEOUT_UNIT))
  }

  @Test
  @Ignore // TODO investigate
  fun `local and remote changes in different files are both applied`() {
    val generalSettings = GeneralSettings.getInstance().init()
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

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
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                                            setOf(fileState), null, emptyMap(), emptySet()))
    //remoteCommunicator.offline = false

    executeAndWaitUntilPushed {
      waitForSettingsToBeApplied(generalSettings, EditorSettingsExternalizable.getInstance()) {
        fireSettingsChanged() // merge will happen here
      }
    }

    assertServerSnapshot {
      fileState {
        GeneralSettings().withState {
          autoSaveFiles = false
        }
      }
      fileState {
        EditorSettingsExternalizable().withState {
          SHOW_INTENTION_BULB = false
        }
      }
    }
    assertFalse(generalSettings.isSaveOnFrameDeactivation)
    assertFalse(EditorSettingsExternalizable.getInstance().isShowIntentionBulb)
  }

/*
  @TestFor(issues = ["IDEA-291623"])
  @Test
  fun `zip file size limit exceed`() {
    val notificationServiceSpy = Mockito.spy<NotificationServiceImpl>()
    ApplicationManager.getApplication().replaceService(NotificationService::class.java, notificationServiceSpy, disposable)

    EditorSettingsExternalizable.getInstance().initModifyAndSave {
      languageBreadcrumbsMap = (1..100000).associate { UUID.randomUUID().toString() to true } // please FIXME if you now a better way to make a fat file
    }
    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    verify(notificationServiceSpy).notifyZipSizeExceed()
  }*/

  //@Test
  fun `only changed components should be reloaded`() {
    TODO()
  }
}
