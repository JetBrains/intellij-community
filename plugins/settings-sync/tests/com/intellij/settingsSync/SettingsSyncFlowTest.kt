package com.intellij.settingsSync

import com.intellij.idea.TestFor
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedScheduledExecutorService
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.write
import com.intellij.util.progress.sleepCancellable
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

internal class SettingsSyncFlowTest : SettingsSyncTestBase() {

  private lateinit var ideMediator: MockSettingsSyncIdeMediator

  @BeforeEach
  internal fun initFields() {
    ideMediator = MockSettingsSyncIdeMediator()
  }

  private fun initSettingsSync(
    initMode: SettingsSyncBridge.InitMode = SettingsSyncBridge.InitMode.JustInit,
    waitForInit: Boolean = true,
  ) {
    SettingsSyncSettings.getInstance().state = SettingsSyncSettings.getInstance().state.withSyncEnabled(true)
    val controls = SettingsSyncMainImpl.init(currentThreadCoroutineScope(), disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(initMode)
    if (waitForInit) {
      timeoutRunBlocking(2.seconds) {
        while (!bridge.isInitialized) {
          sleepCancellable(10)
        }
      }
    }
  }

  @Test
  fun `existing settings should be copied on initialization`() = timeoutRunBlockingAndStopBridge {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }
  }

  @Test
  fun `settings modified between IDE sessions should be logged`() = timeoutRunBlockingAndStopBridge {
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

  @Test
  fun `delete server data`() = timeoutRunBlockingAndStopBridge {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }

    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }

    deleteServerDataAndWait()

    assertVersionOnServerIsDeleted()
    Assertions.assertFalse(SettingsSyncSettings.getInstance().syncEnabled, "Settings sync was not disabled")
  }

  private fun assertVersionOnServerIsDeleted() {
    val versionOnServer = remoteCommunicator.getVersionOnServer()
    Assertions.assertNotNull(versionOnServer, "There is no version on the server")
    Assertions.assertTrue(versionOnServer!!.isDeleted(), "The server snapshot is incorrect: $versionOnServer")
    Assertions.assertTrue(versionOnServer.isEmpty(), "There should be no settings data after deletion: $versionOnServer")
  }

  private fun deleteServerDataAndWait() {
    val cdl = CountDownLatch(1)
    syncSettingsAndWait(SyncSettingsEvent.DeleteServerData {
      cdl.countDown()
    })
    cdl.wait()
  }

  @Test
  fun `disable settings sync if data on server was deleted`() = timeoutRunBlockingAndStopBridge {
    val fileName = "options/laf.xml"
    val initialContent = "LaF Initial"
    configDir.resolve(fileName).write(initialContent)

    SettingsSyncSettings.getInstance().syncEnabled = true
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true)
    remoteCommunicator.prepareFileOnServer(SettingsSnapshot(metaInfo, emptySet(), null, emptyMap(), emptySet()))
    syncSettingsAndWait()
    Assertions.assertFalse(SettingsSyncSettings.getInstance().syncEnabled, "Settings sync was not disabled")
  }

  @Test
  fun `first push after IDE start should update from server if needed`() = timeoutRunBlockingAndStopBridge {
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
    bridge.waitForAllExecuted()
    val pushedSnapshot = remoteCommunicator.getVersionOnServer()
    Assertions.assertNotNull(pushedSnapshot, "Nothing has been pushed")
    pushedSnapshot!!.assertSettingsSnapshot {
      fileState(lafXml, lafContent)
      fileState(editorXml, editorContent)
    }
  }

  @Test
  fun `enable settings sync with Push to Server should overwrite server snapshot instead of merging with it`() = timeoutRunBlockingAndStopBridge {
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

  @Test
  fun `enable settings via Take from Server should log existing settings`() = timeoutRunBlockingAndStopBridge {
    val fileName = "options/laf.xml"
    val initialContent = "LaF Initial"
    configDir.resolve(fileName).write(initialContent)

    val cloudContent = "LaF from Server"
    val snapshot = settingsSnapshot {
      fileState(fileName, cloudContent)
    }

    initSettingsSync(SettingsSyncBridge.InitMode.TakeFromServer(SyncSettingsEvent.CloudChange(snapshot, null)))

    Assertions.assertEquals(cloudContent, (settingsSyncStorage / "options" / "laf.xml").readText(), "Incorrect content")

    assertAppliedToIde("options/laf.xml", cloudContent)

    val dotGit = settingsSyncStorage.resolve(".git")
    FileRepositoryBuilder.create(dotGit.toFile()).use { repository ->
      val git = Git(repository)
      val commits = git.log()
        .add(repository.findRef("master").objectId)
        .add(repository.findRef("ide").objectId)
        .add(repository.findRef("cloud").objectId)
        .call().toList()
      Assertions.assertEquals(3, commits.size, "Unexpected number of commits. Commits: ${commits.joinToString { "'${it.shortMessage}'" }}")
      val (applyCloud, copyExistingSettings, initial) = commits

      Assertions.assertEquals(initialContent, getContent(repository, copyExistingSettings, fileName), "Unexpected content in the 'copy existing settings' commit")
      Assertions.assertEquals(cloudContent, getContent(repository, applyCloud, fileName), "Unexpected content in the 'apply from cloud' commit")
    }
  }

  @Test
  fun `enable settings with migration`() = timeoutRunBlockingAndStopBridge {
    val migration = migrationFromLafXml()

    initSettingsSync(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))

    Assertions.assertEquals("Migration Data", (settingsSyncStorage / "options" / "laf.xml").readText(), "Incorrect content")
  }

  @Test
  fun `enable settings with migration and data on server should merge but prefer server data in case of conflicts`() = timeoutRunBlockingAndStopBridge {
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
  fun `migrated settings with disabled categories should be pushed without settings from these categories`() = timeoutRunBlockingAndStopBridge {
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
  fun `rollback settings and stop sync in case of error`() = timeoutRunBlockingAndStopBridge {
    val fileName = "options/laf.xml"
    val initialContent = "LaF Initial"
    configDir.resolve(fileName).write(initialContent)

    initSettingsSync(SettingsSyncBridge.InitMode.JustInit)

    val errorMessage = "Failed to apply settings: can't lock 'editor.xml'"
    val exceptionToThrow = RuntimeException(errorMessage)
    ideMediator.throwOnApply(exceptionToThrow)

    suppressFailureOnLogError(exceptionToThrow) {
      syncSettingsAndWait(SyncSettingsEvent.CloudChange(settingsSnapshot {
        fileState("options/editor.xml", "Editor change")
      }, null))
    }

    Assertions.assertFalse((settingsSyncStorage / "options" / "editor.xml").exists(), "Partial apply was not rolled back")
    Assertions.assertFalse(SettingsSyncSettings.getInstance().syncEnabled, "Settings sync was not disabled on error")
    Assertions.assertFalse(SettingsSyncStatusTracker.getInstance().isSyncSuccessful(), "Sync is not marked as failed")
    Assertions.assertEquals(errorMessage, SettingsSyncStatusTracker.getInstance().getErrorMessage(), "Incorrect error message")
  }

  @Test
  fun `rollback settings and stop sync if error happens on initialization`() = timeoutRunBlockingAndStopBridge {
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
      timeoutRunBlocking {
        initSettingsSync(SettingsSyncBridge.InitMode.TakeFromServer(SyncSettingsEvent.CloudChange(snapshot, null)))
      }
    }

    Assertions.assertFalse((settingsSyncStorage / "options" / "editor.xml").exists(), "Partial apply was not rolled back")
    Assertions.assertFalse(SettingsSyncSettings.getInstance().syncEnabled, "Settings sync was not disabled on error")
    Assertions.assertFalse(SettingsSyncStatusTracker.getInstance().isSyncSuccessful(), "Sync is not marked as failed")
    Assertions.assertEquals(errorMessage, SettingsSyncStatusTracker.getInstance().getErrorMessage(), "Incorrect error message")
  }

  @Test
  fun `sync settings`() = timeoutRunBlockingAndStopBridge {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }
    initSettingsSync()

    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      fileState("options/editor.xml", "Editor from Server")
    })

    syncSettingsAndWait()
    assertFileWithContent("Editor from Server", (settingsSyncStorage / "options" / "editor.xml"))
    assertFileWithContent("LaF Initial", (settingsSyncStorage / "options" / "laf.xml"))
    assertAppliedToIde("options/editor.xml", "Editor from Server")
  }

  @Test
  fun `concurrent sync does not disable sync during initialization`() = timeoutRunBlockingAndStopBridge {
    writeToConfig {
      fileState("options/laf.xml", "LaF Initial")
    }
    initSettingsSync()

    SettingsSyncSettings.getInstance().syncEnabled = true
    val task1 = Callable {
      bridge.initialize(SettingsSyncBridge.InitMode.PushToServer)
    }
    val task2 = Callable {
      syncSettingsAndWait()
    }

    executeAndWaitUntilPushed {
      ConcurrencyUtil.invokeAll(setOf(task1, task2), createBoundedScheduledExecutorService("SettingsSyncFlowTest", 2))
    }

    Assertions.assertTrue(SettingsSyncSettings.getInstance().syncEnabled, "Settings Sync has been disabled")

    assertServerSnapshot {
      fileState("options/laf.xml", "LaF Initial")
    }
  }

  @Test
  fun `migration should respect deletion on server`() = timeoutRunBlockingAndStopBridge {
    val deletionSnapshot = SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true),
                                            emptySet(), null, emptyMap(), emptySet())
    remoteCommunicator.prepareFileOnServer(deletionSnapshot)

    val migration = migrationFromLafXml()
    initSettingsSync(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))

    Assertions.assertFalse(SettingsSyncSettings.getInstance().syncEnabled, "Settings Sync should be disabled")
    assertVersionOnServerIsDeleted()
  }

  @Test
  fun `regular sync should push if there is nothing on server`() = timeoutRunBlockingAndStopBridge {
    writeToConfig {
      fileState("options/editor.xml", "Editor Initial")
    }
    remoteCommunicator.deleteAllFiles()
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    syncSettingsAndWait()

    assertServerSnapshot {
      fileState("options/editor.xml", "Editor Initial")
    }
  }

  @Test
  fun `unknown additional files should be stored to the history`() = timeoutRunBlockingAndStopBridge {
    initSettingsSync()
    remoteCommunicator.prepareFileOnServer(settingsSnapshot {
      additionalFile("newformat.json", "File with new unknown format")
    })

    syncSettingsAndWait()
    val newFormatJson = settingsSyncStorage / ".metainfo" / "newformat.json"

    assertFileWithContent("File with new unknown format", newFormatJson)
    FileRepositoryBuilder.create(settingsSyncStorage.resolve(".git").toFile()).use { repository ->
      val git = Git(repository)
      val latestCommit = git.log().add(repository.findRef("HEAD").objectId).call().toList().first()
      Assertions.assertEquals("File with new unknown format", getContent(repository, latestCommit, ".metainfo/newformat.json"), "Unexpected content in commit")
    }
  }

  @Test
  fun `unknown additional files should be sent to the server`() = timeoutRunBlockingAndStopBridge {
    (settingsSyncStorage / ".metainfo" / "newformat.json").write("File with new unknown format")
    initSettingsSync(SettingsSyncBridge.InitMode.PushToServer)

    assertServerSnapshot {
      additionalFile("newformat.json", "File with new unknown format")
    }
  }

  @TestFor(issues = ["IDEA-326189"])
  @Test
  fun `create initial commit for empty repo`() = timeoutRunBlockingAndStopBridge {
    val dotGit: Path = settingsSyncStorage.resolve(".git")
    val repository = FileRepositoryBuilder().setGitDir(dotGit.toFile()).setAutonomous(true).readEnvironment().build()
    repository.create()
    initSettingsSync()
    Assertions.assertNotNull(repository.findRef(GitSettingsLog.CLOUD_REF_NAME))
    Assertions.assertNotNull(repository.findRef(GitSettingsLog.IDE_REF_NAME))
  }

  @Test
  fun `disable sync if init failed`() = timeoutRunBlockingAndStopBridge {
    SettingsSyncSettings.getInstance().syncEnabled = true
    val dotGit: Path = settingsSyncStorage.resolve(".git")
    val repository = FileRepositoryBuilder().setGitDir(dotGit.toFile()).setAutonomous(true).readEnvironment().build()
    repository.create()
    val gitignore = settingsSyncStorage.resolve(".gitignore").createParentDirectories().createFile()
    gitignore.write("""
      .idea/workspace.xml
        """.trimIndent())

    val git = Git(repository)
    git.add().addFilepattern(".gitignore").call()
    git.commit().setMessage("init").setNoVerify(true).setSign(false).call()

    val errorMessage = "Failed to collect initial snapshot"
    ideMediator.throwOnGetInitial(RuntimeException(errorMessage))

    LoggedErrorProcessor.executeAndReturnLoggedError {
      timeoutRunBlocking {
        initSettingsSync(waitForInit = false)
        waitUntil {
          SettingsSyncStatusTracker.getInstance().getErrorMessage() != null
        }
      }
    }
    Assertions.assertFalse(SettingsSyncSettings.getInstance().syncEnabled)
  }

  private fun syncSettingsAndWait(event: SyncSettingsEvent = SyncSettingsEvent.SyncRequest) {
    SettingsSyncEvents.getInstance().fireSettingsChanged(event)
    bridge.waitForAllExecuted()
    timeoutRunBlocking(2.seconds) {
      waitUntil("Waiting for file to appear", 2.seconds) {
        bridge.queueSize == 0
      }
    }
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
    Assertions.assertNotNull(value, "$fileSpec was not applied to the IDE")
    Assertions.assertEquals(expectedContent, value, "Incorrect content of the applied $fileSpec")
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
