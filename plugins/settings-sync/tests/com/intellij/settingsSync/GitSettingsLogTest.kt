package com.intellij.settingsSync

import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.SettingsSnapshot.AppInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createFile
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.writeText


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
  private var jbaData: JBAccountInfoService.JBAData? = null

  @Before
  fun setUp() {
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()
    settingsSyncStorage = configDir.resolve("settingsSync")
    jbaData = null
  }

  @Test
  fun `copy files initially`() {
    val keymapContent = "keymapContent"
    val keymapsFolder = configDir / "keymaps"
    (keymapsFolder / "mykeymap.xml").createFile().writeText(keymapContent)
    val editorContent = "editorContent"
    val editorXml = (configDir / "options" / "editor.xml").createFile()
    editorXml.writeText(editorContent)

    val settingsLog = initializeGitSettingsLog(keymapsFolder, editorXml)

    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("keymaps/mykeymap.xml", keymapContent)
      fileState("options/editor.xml", editorContent)
    }
  }

  @Test
  fun `merge conflict should be resolved as last modified`() {
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)

    (settingsSyncStorage / ".git" / "config").writeText("""
      [commit]
          gpgsign = true
      [user]
          signingkey = KEYHERE
      [gpg]
	        format = ssh
      [gpg "ssh"]
        allowedSignersFile = ~/.config/git/allowed_signers""".trimIndent())

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
  fun `plugins state is written to the settings log`() {
    val editorXml = (configDir / "options" / "editor.xml").createFile()
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
    val editorXml = (configDir / "options" / "editor.xml").createFile()
    editorXml.writeText("editorContent")
    val settingsLog = initializeGitSettingsLog(editorXml)
    val jbaEmail = "some-jba-email@jba-mail.com"
    val jbaName = "JBA Name"

    jbaData = JBAccountInfoService.JBAData("some-dummy-user-id", jbaName, jbaEmail)

    settingsLog.applyIdeState(
      settingsSnapshot {
        fileState("options/editor.xml", "Editor Ide")
        fileState("options/ide.general.xml", "General Ide")
      }, "Local changes"
    )
    val personIdent = getRepository().headCommit().authorIdent
    assertEquals(personIdent.emailAddress, jbaEmail)
    assertEquals(personIdent.name, jbaName)
  }

  private fun initializeGitSettingsLog(vararg filesToCopyInitially: Path): GitSettingsLog {
    val settingsLog = GitSettingsLog(settingsSyncStorage, configDir, disposableRule.disposable, { jbaData }) {
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
        if (parent1.id == ide.objectId) {
          assertTrue(parent2.id == cloud.objectId)
        }
        else if (parent1.id == cloud.objectId) {
          assertTrue(parent2.id == ide.objectId)
        }
        else {
          fail("Neither ide nor cloud are parents of master")
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