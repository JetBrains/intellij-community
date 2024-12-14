package com.intellij.settingsSync

import com.intellij.idea.TestFor
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.SettingsSnapshot.AppInfo
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.write
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.*


@RunWith(JUnit4::class)
internal class GitSettingsLogTest {

  private val tempDirManager = TemporaryDirectory()
  private val appRule = ApplicationRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain.outerRule(tempDirManager).around(appRule).around(disposableRule)

  private lateinit var configDir: Path
  private lateinit var settingsSyncStorage: Path
  private var userData: SettingsSyncUserData? = null

  @Before
  fun setUp() {
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()
    settingsSyncStorage = configDir.resolve("settingsSync")
    userData = null
  }

  @Test
  fun `copy files initially`() {
    val keymapContent = "keymapContent"
    val keymapsFolder = configDir / "keymaps"
    (keymapsFolder / "mykeymap.xml").createParentDirectories().createFile().writeText(keymapContent)
    val editorContent = "editorContent"
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText(editorContent)

    val settingsLog = initializeGitSettingsLog(keymapsFolder, editorXml)

    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("keymaps/mykeymap.xml", keymapContent)
      fileState("options/editor.xml", editorContent)
    }
  }

  @Test
  fun `merge conflict should be resolved as last modified`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/editor.xml", "ideEditorContent")
      }, "Local changes"
    )
    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/editor.xml", "cloudEditorContent")
      }, "Remote changes"
    )

    settingsLog.advanceMaster()

    assertEquals("Incorrect content", "cloudEditorContent", (settingsSyncStorage / "options" / "editor.xml").readText())
    assertMasterIsMergeOfIdeAndCloud()
  }

  @Test
  fun `delete-modify merge conflict should be resolved as last modified`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState(FileState.Deleted("options/editor.xml"))
      }, "Local changes"
    )
    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/editor.xml", "cloudEditorContent")
      }, "Remote changes"
    )
    settingsLog.advanceMaster()

    assertEquals("Incorrect content", "cloudEditorContent", (settingsSyncStorage / "options" / "editor.xml").readText())
    assertMasterIsMergeOfIdeAndCloud()

  }

  @Test
  fun `modify-delete merge conflict should be resolved as last modified`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/editor.xml", "moreCloudEditorContent")
      }, "Remote changes"
    )
    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState(FileState.Deleted("options/editor.xml"))
      }, "Local changes"
    )
    settingsLog.advanceMaster()

    assertEquals("Incorrect deleted file content", DELETED_FILE_MARKER, (settingsSyncStorage / "options" / "editor.xml").readText())
    assertMasterIsMergeOfIdeAndCloud()
  }

  @Test
  fun `date of the snapshot`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    val instant = Instant.ofEpochSecond(100500)
    settingsLog.applyCloudState(
      settingsSnapshot(SettingsSnapshot.MetaInfo(instant, AppInfo(UUID.randomUUID(), null, "", "", ""))) {
        fileState("options/editor.xml", "moreCloudEditorContent")
      }, "Remote changes"
    )
    settingsLog.advanceMaster()

    val snapshot = settingsLog.collectCurrentSnapshot()
    assertEquals("The date of the snapshot incorrect", instant, snapshot.metaInfo.dateCreated)
  }

  @Test
  fun `setBranchPosition should reset the working tree as well`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    val masterPosition = settingsLog.getMasterPosition()

    settingsLog.applyCloudState(
      settingsSnapshot(SettingsSnapshot.MetaInfo(Instant.ofEpochSecond(100500), AppInfo(UUID.randomUUID(), null, "", "", ""))) {
        fileState("options/editor.xml", "moreCloudEditorContent")
      }, "Remote changes"
    )
    settingsLog.setCloudPosition(masterPosition)

    assertEquals(masterPosition,
                 settingsLog.getCloudPosition()) // this is just a safety-check that setCloudPosition set the label correctly
    assertEquals("editorContent",
                 (settingsSyncStorage / "options" / "editor.xml").readText()) // this is real test that the cloud changes have gone away
  }

  @Test
  fun `collectCurrentSnapshot should take the master content`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    val editorXmlFileState = "options/editor.xml"
    settingsLog.applyCloudState(
      settingsSnapshot(SettingsSnapshot.MetaInfo(Instant.ofEpochSecond(100500), AppInfo(UUID.randomUUID(), null, "", "", ""))) {
        fileState(editorXmlFileState, "moreCloudEditorContent")
      }, "Remote changes"
    )

    val snapshot = settingsLog.collectCurrentSnapshot()
    val actualFileState = snapshot.fileStates.find { it.file == editorXmlFileState } as FileState.Modified
    assertEquals("editorContent", String(actualFileState.content))
  }

  @Test
  fun `do not fail if commit signature is requested in global config`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    writeGpgSigningOptionToGitConfig()

    settingsLog.forceWriteToMaster(
      settingsSnapshot {
        fileState("options/editor.xml", "ideEditorContent")
      }, "Local changes"
    )
    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("options/editor.xml", "ideEditorContent")
    }
  }

  @Test
  fun `do not fail on merge if commit signature is requested in global config`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    writeGpgSigningOptionToGitConfig()

    // make a non-conflicting merge
    settingsLog.applyCloudState(settingsSnapshot {
      fileState("options/editor.xml", "Cloud Editor")
    }, "Local changes")
    settingsLog.applyIdeState(settingsSnapshot {
      fileState("options/laf.xml", "IDE LaF")
    }, "Local changes")

    settingsLog.advanceMaster()

    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("options/editor.xml", "Cloud Editor")
      fileState("options/laf.xml", "IDE LaF")
    }
  }

  private fun writeGpgSigningOptionToGitConfig() {
    (settingsSyncStorage / ".git" / "config").writeText("""
        [commit]
            gpgsign = true
        [user]
            signingkey = KEYHERE
        [gpg]
          program = /opt/homebrew/bin/gpg""".trimIndent())
  }

  @Test
  fun `do not fail if unknown gpg option is written in global config`() {
    val userHomeDefault = System.getProperty("user.home")
    try {
      val userHome = Files.createTempDirectory("gitSettingsLogTest")
      System.setProperty("user.home", userHome.absolutePathString())

      arrayOf<FileAttribute<*>>()
      val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
      editorXml.writeText("editorContent")
      (userHome / ".gitconfig").writeText("""
      [commit]
          gpgsign = true
      [user]
          signingkey = KEYHERE
      [gpg]
	        format = ssh
      [gpg "ssh"]
        allowedSignersFile = ~/.config/git/allowed_signers""".trimIndent())
      val settingsLog = initializeGitSettingsLog(editorXml)

      settingsLog.forceWriteToMaster(
        settingsSnapshot {
          fileState("options/editor.xml", "ideEditorContent")
        }, "Local changes"
      )
      settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
        fileState("options/editor.xml", "ideEditorContent")
      }
    }
    finally {
      System.setProperty("user.home", userHomeDefault)
    }
  }

  @Test
  @TestFor(issues = ["IJPL-156917"])
  fun `should not attempt to read global configs`() {
    val userHomeDefault = System.getProperty("user.home")
    try {
      val userHome = Files.createTempDirectory("gitSettingsLogTest")
      System.setProperty("user.home", userHome.absolutePathString())

      arrayOf<FileAttribute<*>>()
      val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
      editorXml.writeText("editorContent")
      val config1 = userHome / "config.1"
      val config2 = userHome / "config.2"
      val config3 = userHome / "config.3"
      val settingsLog = initializeGitSettingsLog(editorXml)
      val actualSystemReader = SystemReader.getInstance()
      val userConfigRead = AtomicBoolean(false)
      val systemConfigRead = AtomicBoolean(false)
      val jgitConfigRead = AtomicBoolean(false)
      SystemReader.setInstance(object : SystemReader() {
        override fun getHostname() = actualSystemReader.hostname
        override fun getenv(variable: String?) = actualSystemReader.getenv(variable)
        override fun getProperty(key: String?) = actualSystemReader.getProperty(key)

        override fun openUserConfig(parent: Config?, fs: FS?): FileBasedConfig {
          userConfigRead.set(true)
          return FileBasedConfig(config1.toFile(), fs)
        }

        override fun openSystemConfig(parent: Config?, fs: FS?): FileBasedConfig {
          systemConfigRead.set(true)
          return FileBasedConfig(config2.toFile(), fs)
        }

        override fun openJGitConfig(parent: Config?, fs: FS?): FileBasedConfig {
          jgitConfigRead.set(true)
          return FileBasedConfig(config3.toFile(), fs)
        }

        override fun getCurrentTime() = actualSystemReader.currentTime

        override fun getTimezone(`when`: Long) = actualSystemReader.getTimezone(`when`)
      })
      settingsLog.forceWriteToMaster(
        settingsSnapshot {
          fileState("options/editor.xml", "ideEditorContent")
        }, "Local changes"
      )
      settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
        fileState("options/editor.xml", "ideEditorContent")
      }
      assertFalse(jgitConfigRead.get())
      assertFalse(systemConfigRead.get())
      assertFalse(userConfigRead.get())
    }
    finally {
      System.setProperty("user.home", userHomeDefault)
    }

  }

  @Test
  fun `plugins state is written to the settings log`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    val id = "com.jetbrains.plugin"
    val dependencies = setOf("com.intellij.modules.lang")
    settingsLog.forceWriteToMaster(settingsSnapshot {
      plugin(id, enabled = true, category = SettingsCategory.UI, dependencies = dependencies)
    }, "Install plugin")

    val snapshot = settingsLog.collectCurrentSnapshot()
    snapshot.assertSettingsSnapshot {
      fileState("options/editor.xml", "editorContent")
      plugin(id, enabled = true, SettingsCategory.UI, dependencies)
    }
  }

  @Test
  fun `merge conflict in plugins-json should be resolved smartly`() {
    val editorXml = (configDir / "options" / "editor.xml").write("Editor Initial")
    val settingsLog = initializeGitSettingsLog(editorXml)

    settingsLog.applyIdeState(
      settingsSnapshot {
        plugin("A", true)
      }, "Local"
    )
    settingsLog.applyCloudState(
      settingsSnapshot {
        plugin("B", true)
      }, "Remote"
    )
    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor IDE")
      }, "Local"
    )
    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/laf.xml", "LaF Cloud")
      }, "Remote"
    )

    settingsLog.advanceMaster()

    val snapshot = settingsLog.collectCurrentSnapshot()
    snapshot.assertSettingsSnapshot {
      fileState("options/editor.xml", "Editor IDE")
      fileState("options/laf.xml", "LaF Cloud")
      plugin("A", true)
      plugin("B", true)
    }
  }

  @Test
  fun `merge conflict should be resolved as last modified for the particular file`() {
    val editorXml = (configDir / "options" / "editor.xml").write("Editor Initial")
    val lafXml = (configDir / "options" / "laf.xml").write("LaF Initial")
    val generalXml = (configDir / "options" / "ide.general.xml").write("General Initial")
    val diffXml = (configDir / "options" / "diff.xml").write("Diff Initial")

    val settingsLog = initializeGitSettingsLog(lafXml, editorXml, generalXml)

    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor Ide")
        fileState("options/ide.general.xml", "General Ide")
      }, "Local changes"
    )
    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/laf.xml", "LaF Cloud")
        fileState("options/diff.xml", "Diff Cloud")
      }, "Remote changes"
    )
    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor Cloud")
      }, "Remote changes"
    )
    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/laf.xml", "LaF Ide")
      }, "Local changes"
    )

    settingsLog.advanceMaster()

    assertEquals("Incorrect content", "Editor Cloud", (settingsSyncStorage / "options" / "editor.xml").readText())
    assertEquals("Incorrect content", "LaF Ide", (settingsSyncStorage / "options" / "laf.xml").readText())
    assertEquals("Incorrect content", "Diff Cloud", (settingsSyncStorage / "options" / "diff.xml").readText())
    assertEquals("Incorrect content", "General Ide", (settingsSyncStorage / "options" / "ide.general.xml").readText())
    assertMasterIsMergeOfIdeAndCloud()
  }

  @Test
  fun `use username from JBA`() {
    val jbaEmail = "some-jba-email@jba-mail.com"
    val jbaName = "JBA Name"

    userData = SettingsSyncUserData(jbaName, jbaEmail)
    checkUsernameEmail(jbaName, jbaEmail)
  }

  @Test
  @TestFor(issues = ["EA-844607"])
  fun `use empty email if JBA doesn't provide one`() {
    val jbaName = "JBA Name 2"

    userData = SettingsSyncUserData(jbaName, null)
    checkUsernameEmail(jbaName, "")
  }

  @Test
  @TestFor(issues = ["EA-844607"])
  fun `use empty name if JBA doesn't provide one`() {
    userData = SettingsSyncUserData(null, null)
    checkUsernameEmail("", "")
  }

  @Test
  @TestFor(issues = ["IDEA-340175"])
  fun `unlock git refs heads`() {
    val gitSettingsLog = initializeGitSettingsLog()
    Disposer.dispose(gitSettingsLog)
    val headsDir = settingsSyncStorage / ".git" / "refs" / "heads"
    val branchNames = headsDir.listDirectoryEntries().map { it.name }
    assertTrue(branchNames.containsAll(listOf("master", "cloud", "ide")))
    val locks = mutableListOf<Path>()
    branchNames.forEach { branchName ->
      locks.add((headsDir / "$branchName.lock").also { path -> path.createFile() })
    }
    try {
      val newGitSettingsLog = initializeGitSettingsLog()
      fail("Should have failed")
    } catch (ex: Exception) {}

    locks.forEach {
      it.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 7000L))
    }
    val newGitSettingsLog = initializeGitSettingsLog()
    assertTrue(locks.none {it.exists()})

  }

  @Test
  @TestFor(issues = ["IDEA-305967"])
  fun `unlock git if locked`() {
    val gitSettingsLog = initializeGitSettingsLog()
    Disposer.dispose(gitSettingsLog)
    val indexLock  = settingsSyncStorage / ".git" / "index.lock"
    indexLock.createFile()
    val headLock  = settingsSyncStorage / ".git" / "HEAD.lock"
    headLock.createFile()
    try {
      val newGitSettingsLog = initializeGitSettingsLog()
      fail("Should have failed")
    } catch (ex: Exception) {

    }
    indexLock.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 7000L))
    try {
      val newGitSettingsLog = initializeGitSettingsLog()
      fail("Should have failed")
    } catch (ex: Exception) {

    }
    assertFalse(indexLock.exists())

    headLock.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 7000L))
    val newGitSettingsLog = initializeGitSettingsLog()
    assertFalse(headLock.exists())
  }

  @Test
  fun `test reset to state`() {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    settingsLog.applyIdeState(settingsSnapshot {
      fileState("options/editor.xml", "State 1")
    }, "Local changes")
    val state1Hash = getRepository().headCommit().id.name
    settingsLog.applyIdeState(settingsSnapshot {
      fileState("options/editor.xml", "State 2")
      fileState("options/laf.xml", "Laf State 2")
    }, "Local changes")
    settingsLog.advanceMaster()

    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("options/editor.xml", "State 2")
      fileState("options/laf.xml", "Laf State 2")
    }

    settingsLog.restoreStateAt(state1Hash.toString())
    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("options/editor.xml", "State 1")
    }
  }

  @Test
  @TestFor(issues = ["IJPL-13080"])
  fun `drop and reinit settings sync if cannot init`() {
    val size = 16384
    drop_and_reinit_base( {
      val indexFile = getRepository().indexFile
      val zeroBytesArray = ByteArray(size)
      indexFile.writeBytes(zeroBytesArray)
      assertEquals(size.toLong(), indexFile.length())
    }, {
      val indexFile = getRepository().indexFile
      assertNotEquals(size, indexFile.length())
    })
  }

  @Test
  @TestFor(issues = ["IJPL-173307"])
  fun `drop and reinit settings sync if cannot init 2`() {
    drop_and_reinit_base( {
      val dir = getRepository().directory
      val refs = dir.resolve("refs")
      FileUtil.deleteRecursively(refs.toPath())
      assertFalse(refs.exists())
    }, {
      val dir = getRepository().directory
      val refs = dir.resolve("refs")
      assertTrue(refs.exists())
    })
  }

  private fun drop_and_reinit_base(damageAction: ()->Unit, verifyAction: ()->Unit) {
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    val editorContent = "editorContent"
    val state1 = "State 1"

    editorXml.writeText(editorContent)
    val settingsLog = initializeGitSettingsLog(editorXml)

    settingsLog.applyIdeState(settingsSnapshot {
      fileState("options/editor.xml", state1)
    }, "Local changes")
    val editorXmlSync = settingsSyncStorage / "options" / "editor.xml"
    assertEquals(state1, editorXmlSync.readText())


    settingsLog.applyIdeState(settingsSnapshot {
      fileState("options/editor.xml", state1)
    }, "Local changes")

    damageAction()

    try {
      DirCache.read(getRepository())
    }
    catch (ex: Exception) {
    }

    initializeGitSettingsLog(editorXml)
    getRepository().indexFile.length()
    assertTrue(editorXmlSync.exists() && editorXmlSync.readText() == editorContent)

    verifyAction()

    try {
      DirCache.read(getRepository())
    }
    catch (ex: Exception) {
      fail("Shouldn't fail: ${ex.message}")
    }

  }

  @Test
  @TestFor(issues = ["IJPL-13061"])
  fun `drop for unfinished modifications`() {
    val editorXml = (configDir / "options" / "editor.xml").write("Editor Initial")
    val lafXml = (configDir / "options" / "laf.xml").write("LaF Initial")
    val generalXml = (configDir / "options" / "ide.general.xml").write("General Initial")

    val settingsLog = initializeGitSettingsLog(lafXml, editorXml)


    settingsLog.applyCloudState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor Cloud")
        fileState("options/ide.general.xml", "General Cloud")
      }, "Remote changes"
    )
    settingsLog.advanceMaster()

    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor Ide")
        fileState("options/ide.general.xml", "General Ide")
      }, "Local changes"
    )

    generalXml.write("General Cloud")
    (configDir / "settingsSync" / "options" / "ide.general.xml").write("General New")
    val repository = FileRepositoryBuilder()
      .setGitDir((configDir / "settingsSync"/ ".git").toFile())
      .setAutonomous(true).readEnvironment().build()
    val git = Git(repository)
    val addCommand = git.add()
    addCommand.addFilepattern("options/ide.general.xml")
    addCommand.call()

    initializeGitSettingsLog(lafXml, editorXml, generalXml)
  }

  private fun checkUsernameEmail(expectedName: String, expectedEmail: String) {
    arrayOf<FileAttribute<*>>()
    val editorXml = (configDir / "options" / "editor.xml").createParentDirectories().createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)
    (settingsSyncStorage / ".git" / "config").writeText("""
[user]
        name = Gawr Gura
        email = just-email@non-existing.addr
""".trimIndent())
    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor Ide")
        fileState("options/ide.general.xml", "General Ide")
      }, "Local changes"
    )
    val headCommit = getRepository().headCommit()
    val author = headCommit.authorIdent
    val committer = headCommit.committerIdent
    assertEquals(expectedEmail, author.emailAddress)
    assertEquals(expectedEmail, committer.emailAddress)
    assertEquals(expectedName, author.name)
    assertEquals(expectedName, committer.name)
  }

  private fun initializeGitSettingsLog(vararg filesToCopyInitially: Path): GitSettingsLog {
    val settingsLog = GitSettingsLog(settingsSyncStorage, configDir, disposableRule.disposable, { userData }) {
      val fileStates = collectFileStatesFromFiles(filesToCopyInitially.toSet(), configDir)
      SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), null), fileStates, plugins = null, emptyMap(), emptySet())
    }
    settingsLog.initialize()
    settingsLog.logExistingSettings()
    val masterPosition = settingsLog.advanceMaster()
    settingsLog.setCloudPosition(masterPosition)
    return settingsLog
  }

  private fun getRepository(): Repository {
    val dotGit = settingsSyncStorage.resolve(".git")
    return FileRepositoryBuilder.create(dotGit.toFile())
  }

  private fun assertMasterIsMergeOfIdeAndCloud() {
    getRepository().use { repository ->
      val walk = RevWalk(repository)
      try {
        val commit: RevCommit = walk.parseCommit(repository.findRef("master").objectId)
        walk.markStart(commit)
        val parents = commit.parents
        assertEquals(2, parents.size)
        val ide = repository.findRef("ide")!!
        val cloud = repository.findRef("cloud")!!
        val (parent1, parent2) = parents
        when (parent1.id) {
          ide.objectId -> {
            assertTrue(parent2.id == cloud.objectId)
          }
          cloud.objectId -> {
            assertTrue(parent2.id == ide.objectId)
          }
          else -> {
            fail("Neither ide nor cloud are parents of master")
          }
        }
        walk.dispose()
      }
      finally {
        walk.dispose()
        walk.close()
      }
    }
  }
}
