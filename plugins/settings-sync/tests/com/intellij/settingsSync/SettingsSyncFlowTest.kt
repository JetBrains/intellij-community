package com.intellij.settingsSync

import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedScheduledExecutorService
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText

@RunWith(JUnit4::class)
internal class SettingsSyncFlowTest : SettingsSyncTestBase() {

  private lateinit var ideMediator: MockSettingsSyncIdeMediator

  @Before
  internal fun initFields() {
    ideMediator = MockSettingsSyncIdeMediator()
  }

  private fun initSettingsSync(initMode: SettingsSyncBridge.InitMode = SettingsSyncBridge.InitMode.JustInit) {
    val controls = SettingsSyncMain.init(application, disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(initMode)
  }

  @Test fun `existing settings should be copied on initialization`() {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }
  }

  @Test fun `settings modified between IDE sessions should be logged`() {
    // emulate first session with initialization
    val fileName = "options/laf.xml"
    val file = configDir.resolve(fileName).write("LaF Initial")
    val log = GitSettingsLog(settingsSyncStorage, configDir, disposable, { null },
                             initialSnapshotProvider = { MockSettingsSyncIdeMediator.getAllFilesFromSettingsAsSnapshot(configDir) })
    log.initialize()
    log.logExistingSettings()

    // modify between sessions
    val contentBetweenSessions = "LaF Between Sessions"
    file.writeText(contentBetweenSessions)

    initSettingsSync()

    assertServerSnapshot {
      fileState(fileName, contentBetweenSessions)
    }
  }

  @Test fun `delete server data`() {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }

    deleteServerDataAndWait()

    assertVersionOnServerIsDeleted()
    assertFalse("Settings sync was not disabled", SettingsSyncSettings.getInstance().syncEnabled)
  }

  private fun assertVersionOnServerIsDeleted() {
    val versionOnServer = remoteCommunicator.getVersionOnServer()
    assertNotNull("There is no version on the server", versionOnServer)
    assertTrue("The server snapshot is incorrect: $versionOnServer", versionOnServer!!.isDeleted())
    assertTrue("There should be no settings data after deletion: $versionOnServer", versionOnServer.isEmpty())
  }

  private fun deleteServerDataAndWait() {
    val cdl = CountDownLatch(1)
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeleteServerData {
      cdl.countDown()
    })
    cdl.wait()
  }

  @Test fun `disable settings sync if data on server was deleted`() {
    val fileName = "options/laf.xml"
    val initialContent = "LaF Initial"
    configDir.resolve(fileName).write(initialContent)

    SettingsSyncSettings.getInstance().syncEnabled = true
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true)
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(metaInfo, emptySet(), null, emptyMap(), emptySet()))
    syncSettingsAndWait()

    assertFalse("Settings sync was not disabled", SettingsSyncSettings.getInstance().syncEnabled)
  }

  @Test fun `first push after IDE start should update from server if needed`() {
    // prepare settings on server
    val editorXml = "options/editor.xml"
    val editorContent = "Editor from Server"
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      fileState(editorXml, editorContent)
    })

    // prepare local settings
    val lafXml = "options/laf.xml"
    val lafContent = "LaF Initial"
    configDir.resolve(lafXml).write(lafContent)

    initSettingsSync()

    val pushedSnapshot = remoteCommunicator.getVersionOnServer()
    assertNotNull("Nothing has been pushed", pushedSnapshot)
    pushedSnapshot!!.assertSettingsSnapshot {
      fileState(lafXml, lafContent)
      fileState(editorXml, editorContent)
    }
  }

  @Test fun `enable settings sync with Push to Server should overwrite server snapshot instead of merging with it`() {
    // prepare settings on server
    val editorXml = "options/editor.xml"
    val editorContent = "Editor from Server"
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      fileState(editorXml, editorContent)
    })

    // prepare local settings
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }
  }

  @Test fun `enable settings via Take from Server should log existing settings`() {
    val fileName = "options/laf.xml"
    val initialContent = "LaF Initial"
    configDir.resolve(fileName).write(initialContent)

    val cloudContent = "LaF from Server"
    val snapshot = settingsSnapshot {
      fileState(fileName, cloudContent)
    }

    initSettingsSync(SettingsSyncBridge.InitMode.TakeFromServer(SyncSettingsEvent.CloudChange(snapshot, null)))

    assertEquals("Incorrect content", cloudContent, (settingsSyncStorage / "options" / "laf.xml").readText())

    assertAppliedToIde("options/laf.xml", cloudContent)

    val dotGit = settingsSyncStorage.resolve(".git")
    FileRepositoryBuilder.create(dotGit.toFile()).use { repository ->
      val git = Git(repository)
      val commits = git.log()
        .add(repository.findRef("master").objectId)
        .add(repository.findRef("ide").objectId)
        .add(repository.findRef("cloud").objectId)
        .call().toList()
      assertEquals("Unexpected number of commits. Commits: ${commits.joinToString { "'${it.shortMessage}'" }}", 3, commits.size)
      val (applyCloud, copyExistingSettings, initial) = commits

      assertEquals("Unexpected content in the 'copy existing settings' commit", initialContent, getContent(repository, copyExistingSettings, fileName))
      assertEquals("Unexpected content in the 'apply from cloud' commit", cloudContent, getContent(repository, applyCloud, fileName))
    }
  }

  @Test fun `enable settings with migration`() {
    val migration = migrationFromLafXml()

    initSettingsSync(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))

    assertEquals("Incorrect content", "Migration Data", (settingsSyncStorage / "options" / "laf.xml").readText())
  }

  @Test fun `enable settings with migration and data on server should merge but prefer server data in case of conflicts`() {
    val migration = migration(settingsSnapshot {
      fileState("options/laf.xml", "Migration Data")
      fileState("options/editor.xml", "Migration Data")
    })
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      fileState("options/laf.xml", "Server Data")
      fileState("options/keymap.xml", "Server Data")
    })

    initSettingsSync(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))

    assertFileWithContent("Migration Data", (settingsSyncStorage / "options" / "editor.xml"))
    assertFileWithContent("Server Data", (settingsSyncStorage / "options" / "laf.xml"))
    assertFileWithContent("Server Data", (settingsSyncStorage / "options" / "keymap.xml"))
  }

  //@Test
  // the implementation is postponed
  fun `migrated settings with disabled categories should be pushed without settings from these categories`() {
    val migration = object : SettingsSyncMigration {
      override fun isLocalDataAvailable(appConfigDir: Path): Boolean = true

      override fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot {
        return settingsSnapshot {
          fileState("options/laf.xml", "Migration Data")
          fileState("options/keymap.xml", "Migration Data")
        }
      }
      override fun migrateCategoriesSyncStatus(appConfigDir: Path, syncSettings: SettingsSyncSettings) {
        syncSettings.setCategoryEnabled(SettingsCategory.UI, false)
      }
    }
    initSettingsSync(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))

    remoteCommunicator.getVersionOnServer()!!.assertSettingsSnapshot {
      fileState("options/keymap.xml", "Migration Data")
    }
  }

  @Test
  fun `rollback settings and stop sync in case of error`() {
    val fileName = "options/laf.xml"
    val initialContent = "LaF Initial"
    configDir.resolve(fileName).write(initialContent)

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val errorMessage = "Failed to apply settings: can't lock 'editor.xml'"
    val exceptionToThrow = RuntimeException(errorMessage)
    ideMediator.throwOnApply(exceptionToThrow)

    suppressFailureOnLogError(exceptionToThrow) {
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.CloudChange(settingsSnapshot {
        fileState("options/editor.xml", "Editor change")
      }, null))
      bridge.waitForAllExecuted()
    }

    assertFalse("Partial apply was not rolled back", (settingsSyncStorage / "options" / "editor.xml").exists())
    assertFalse("Settings sync was not disabled on error", SettingsSyncSettings.getInstance().syncEnabled)
    assertFalse("Sync is not marked as failed", SettingsSyncStatusTracker.getInstance().isSyncSuccessful())
    assertEquals("Incorrect error message", errorMessage, SettingsSyncStatusTracker.getInstance().getErrorMessage())
  }

  @Test
  fun `rollback settings and stop sync if error happens on initialization`() {
    val initialContent = "LaF Initial"
    configDir.resolve("options/laf.xml").write(initialContent)

    val cloudContent = "Editor from Server"
    val snapshot = settingsSnapshot {
      fileState("options/editor.xml", cloudContent)
    }

    val errorMessage = "Failed to apply settings: can't lock 'editor.xml'"
    val exceptionToThrow = RuntimeException(errorMessage)
    ideMediator.throwOnApply(exceptionToThrow)

    suppressFailureOnLogError(exceptionToThrow) {
      initSettingsSync(SettingsSyncBridge.InitMode.TakeFromServer(SyncSettingsEvent.CloudChange(snapshot, null)))
    }

    assertFalse("Partial apply was not rolled back", (settingsSyncStorage / "options" / "editor.xml").exists())
    assertFalse("Settings sync was not disabled on error", SettingsSyncSettings.getInstance().syncEnabled)
    assertFalse("Sync is not marked as failed", SettingsSyncStatusTracker.getInstance().isSyncSuccessful())
    assertEquals("Incorrect error message", errorMessage, SettingsSyncStatusTracker.getInstance().getErrorMessage())
  }

  @Test fun `sync settings`() {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }
    val controls = SettingsSyncMain.init(application, disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(SettingsSyncBridge.InitMode.JustInit)

    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      fileState("options/editor.xml", "Editor from Server")
    })

    syncSettingsAndWait()

    assertFileWithContent("Editor from Server", (settingsSyncStorage / "options" / "editor.xml"))
    assertFileWithContent("LaF Initial", (settingsSyncStorage / "options" / "laf.xml"))
    assertAppliedToIde("options/editor.xml", "Editor from Server")
  }

  @Test fun `concurrent sync does not disable sync during initialization`() {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }
    val controls = SettingsSyncMain.init(application, disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge

    SettingsSyncSettings.getInstance().syncEnabled = true
    val task1 = Callable {
      bridge.initialize(SettingsSyncBridge.InitMode.PushToServer)
    }
    val task2 = Callable {
      SettingsSynchronizer.syncSettings()
    }

    executeAndWaitUntilPushed {
      ConcurrencyUtil.invokeAll(setOf(task1, task2), createBoundedScheduledExecutorService("SettingsSyncFlowTest", 2))
    }

    assertTrue("Settings Sync has been disabled", SettingsSyncSettings.getInstance().syncEnabled)

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }
  }

  @Test fun `migration should respect deletion on server`() {
    val deletionSnapshot = SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true),
                                            emptySet(), null, emptyMap(), emptySet())
    remoteCommunicator.prepareFileOnServer(deletionSnapshot)

    val migration = migrationFromLafXml()
    initSettingsSync(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))

    assertFalse("Settings Sync should be disabled", SettingsSyncSettings.getInstance().syncEnabled)
    assertVersionOnServerIsDeleted()
  }

  @Test fun `regular sync should push if there is nothing on server`() {
    writeToConfig {
      fileState("options/editor.xml", "Editor Initial")
    }
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)
    remoteCommunicator.deleteAllFiles()

    syncSettingsAndWait()

    assertServerSnapshot {
      fileState("options/editor.xml", "Editor Initial")
    }
  }

  @Test fun `unknown additional files should be stored to the history`() {
    initSettingsSync()
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      additionalFile("newformat.json", "File with new unknown format")
    })

    syncSettingsAndWait()

    assertFileWithContent("File with new unknown format", settingsSyncStorage / ".metainfo" / "newformat.json")
    FileRepositoryBuilder.create(settingsSyncStorage.resolve(".git").toFile()).use { repository ->
      val git = Git(repository)
      val latestCommit = git.log().add(repository.findRef("HEAD").objectId).call().toList().first()
      assertEquals("Unexpected content in commit",
                   "File with new unknown format",
                   getContent(repository, latestCommit, ".metainfo/newformat.json"))
    }
  }

  @Test fun `unknown additional files should be sent to the server`() {
    (settingsSyncStorage / ".metainfo" / "newformat.json").write("File with new unknown format")
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      additionalFile("newformat.json", "File with new unknown format")
    }
  }

  private fun syncSettingsAndWait() {
    SettingsSynchronizer.syncSettings()
    bridge.waitForAllExecuted()
  }

  private fun suppressFailureOnLogError(expectedException: RuntimeException, activity: () -> Unit) {
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        return if (t == expectedException) Action.NONE else Action.ALL
      }
    }) {
      activity()
    }
  }

  private fun migrationFromLafXml() = migration(
    settingsSnapshot {
      fileState("options/laf.xml", "Migration Data")
    }
  )

  private fun migration(snapshotToMigrate: SettingsSnapshot) = object : SettingsSyncMigration {
    override fun isLocalDataAvailable(appConfigDir: Path): Boolean {
      throw UnsupportedOperationException("Should not have been called")
    }

    override fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot {
      return snapshotToMigrate
    }

    override fun migrateCategoriesSyncStatus(appConfigDir: Path, syncSettings: SettingsSyncSettings) {
    }
  }

  private fun assertAppliedToIde(fileSpec: String, expectedContent: String) {
    val value = ideMediator.files[fileSpec]
    assertNotNull("$fileSpec was not applied to the IDE", value)
    assertEquals("Incorrect content of the applied $fileSpec", expectedContent, value)
  }

  private fun getContent(repository: Repository, commit: RevCommit, path: String): String {
    TreeWalk.forPath(repository, path, commit.tree).use { treeWalk ->
      repository.newObjectReader().use { objectReader ->
        val blob = treeWalk.getObjectId(0)
        return String(objectReader.open(blob).bytes, StandardCharsets.UTF_8)
      }
    }
  }
}