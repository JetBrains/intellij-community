package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.util.io.createFile
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import com.intellij.util.io.write
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.lib.Constants.R_HEADS
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.regex.Pattern
import kotlin.io.path.div

internal class GitSettingsLog(private val settingsSyncStorage: Path,
                              private val rootConfigPath: Path,
                              parentDisposable: Disposable,
                              private val initialSnapshotProvider: (SettingsSnapshot) -> SettingsSnapshot
) : SettingsLog, Disposable {

  private lateinit var repository: Repository
  private lateinit var git: Git

  private val master: Ref get() = repository.findRef(MASTER_REF_NAME)!!
  private val ide: Ref get() = repository.findRef(IDE_REF_NAME)!!
  private val cloud: Ref get() = repository.findRef(CLOUD_REF_NAME)!!

  private val pluginsFile = settingsSyncStorage / METAINFO_FOLDER / PLUGINS_FILE
  private val json = Json { prettyPrint = true }

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun initialize() {
    val dotGit = settingsSyncStorage.resolve(".git")
    repository = FileRepositoryBuilder.create(dotGit.toFile())
    git = Git(repository)

    val newRepository = !dotGit.exists()
    if (newRepository) {
      LOG.info("Initializing new Git repository for Settings Sync at $settingsSyncStorage")
      repository.create()
      initRepository(repository)
    }

    createBranchIfNeeded(MASTER_REF_NAME, newRepository)
    createBranchIfNeeded(CLOUD_REF_NAME, newRepository)
    createBranchIfNeeded(IDE_REF_NAME, newRepository)
  }

  override fun logExistingSettings() {
    copyExistingSettings()
  }

  private fun createBranchIfNeeded(name: String, newRepository: Boolean) {
    val ref = repository.findRef(name)
    if (ref == null) {
      val head = repository.headCommit()
      if (!newRepository) {
        LOG.warn("Ref with name $name not found in existing repository. Recreating at position of HEAD@${head.toObjectId().short}")
      }
      git.branchCreate().setName(name).setStartPoint(head).call()
    }
  }

  private fun copyExistingSettings() {
    LOG.info("Copying existing settings from $rootConfigPath to $settingsSyncStorage")
    val snapshot = initialSnapshotProvider(collectCurrentSnapshot())
    applyState(IDE_REF_NAME, snapshot, "Copy current configs", warnAboutEmptySnapshot = false)
  }

  private fun initRepository(repository: Repository?) {
    val gitignore = settingsSyncStorage.resolve(".gitignore").createFile()
    gitignore.write("""
          event-log-metadata
          jdbc-drivers
          ssl
          port
          port.lock
          updatedBrokenPlugins.db
          
        """.trimIndent())

    val git = Git(repository)
    git.add().addFilepattern(".gitignore").call()
    commit("Initial")
  }

  override fun applyIdeState(snapshot: SettingsSnapshot, message: String) {
    applyState(IDE_REF_NAME, snapshot, message)
  }

  override fun applyCloudState(snapshot: SettingsSnapshot, message: String) {
    applyState(CLOUD_REF_NAME, snapshot, message)
  }

  override fun forceWriteToMaster(snapshot: SettingsSnapshot, message: String): SettingsLog.Position {
    applyState(MASTER_REF_NAME, snapshot, message)
    return getMasterPosition()
  }

  private fun applyState(refName: String, snapshot: SettingsSnapshot, message: String, warnAboutEmptySnapshot: Boolean = true) {
    if (snapshot.isEmpty() && warnAboutEmptySnapshot) {
      LOG.error("Empty snapshot, requested to apply on branch '$refName' with message '$message'")
      return
    }

    git.checkout().setName(refName).call()
    applySnapshotAndCommit(refName, snapshot, message)
  }

  private fun applySnapshotAndCommit(refName: String, snapshot: SettingsSnapshot, message: String) {
    // todo check repository consistency before each operation: that we're on master, that rb is deleted, that there're no uncommitted changes

    LOG.info("Applying settings changes to branch $refName: " + snapshot.fileStates.joinToString(limit = 5) { it.file })
    val addCommand = git.add()
    for (fileState in snapshot.fileStates) {
      val file = settingsSyncStorage.resolve(fileState.file)
      when (fileState) {
        is FileState.Modified -> file.write(fileState.content)
        is FileState.Deleted -> file.write(DELETED_FILE_MARKER)
      }
      addCommand.addFilepattern(fileState.file)
    }

    if (snapshot.plugins != null) {
      val sortedState = SettingsSyncPluginsState(snapshot.plugins.plugins.toSortedMap())
      val pluginsState = json.encodeToString(sortedState)
      pluginsFile.write(pluginsState)
      addCommand.addFilepattern("$METAINFO_FOLDER/$PLUGINS_FILE")
    }

    addCommand.call()

    commit(message, snapshot.metaInfo.dateCreated)
  }

  private fun commit(message: String, dateCreated: Instant? = null) {
    try {
      // Don't allow empty commit: sometimes the stream provider can notify about changes but there are no actual changes on disk
      val mockGpgConfig = GpgConfig("", GpgConfig.GpgFormat.OPENPGP, "")
      val commit = git.commit()
        .setMessage(message)
        .setAllowEmpty(false)
        .setNoVerify(true)
        .setSign(false)
        .setGpgConfig(mockGpgConfig)
        .call()

      if (dateCreated != null) {
        recordCreationDate(commit, dateCreated)
      }
    }
    catch (e: EmptyCommitException) {
      LOG.info("No actual changes in the settings")
    }
  }

  // emulating --author-date since there is no API in JGit to provide this information,
  // and because the date is 1-second granularity on some OSs
  private fun recordCreationDate(commit: RevCommit, dateCreated: Instant) {
    val date = if (dateCreated <= Instant.now()) {
      dateCreated
    }
    else {
      LOG.error("Date of the snapshot happens in future: $dateCreated")
      Instant.now()
    }

    git.notesAdd().setMessage("$DATE_PREFIX${date.toEpochMilli()}").setObjectId(commit).call()
  }

  override fun dispose() {
    if (this::repository.isInitialized) {
      repository.close()   // todo synchronize
    }
  }

  override fun collectCurrentSnapshot(): SettingsSnapshot {
    // todo check repository consistency, e.g. there should be no uncommitted changes
    git.checkout().setName(MASTER_REF_NAME).setForced(true).call()

    val lastModifiedDate = getDate(getBranchTip(master))
    val files = settingsSyncStorage.toFile().walkTopDown()
      .onEnter { it.name != ".git" && it.name != METAINFO_FOLDER }
      .filter { it.isFile && it.name != ".gitignore" }
      .mapTo(HashSet()) { getFileStateFromFileWithDeletedMarker(it.toPath(), settingsSyncStorage) }

    val pluginsState = readPluginsState()
    return SettingsSnapshot(MetaInfo(lastModifiedDate, getLocalApplicationInfo()), files, pluginsState)
  }

  private fun readPluginsState(): SettingsSyncPluginsState? {
    try {
      if (pluginsFile.exists()) {
        return json.decodeFromString<SettingsSyncPluginsState>(pluginsFile.readText())
      }
    }
    catch (e: Exception) {
      LOG.error("Couldn't parse $pluginsFile", e)
    }
    return null
  }

  override fun getIdePosition(): SettingsLog.Position {
    return getPosition(ide)
  }

  override fun getCloudPosition(): SettingsLog.Position {
    return getPosition(cloud)
  }

  override fun getMasterPosition(): SettingsLog.Position {
    return getPosition(master)
  }

  private fun getPosition(ref: Ref) = BranchPosition(ref.objectId.name())

  override fun setIdePosition(position: SettingsLog.Position) {
    updateBranchPosition(IDE_REF_NAME, position)
  }

  override fun setCloudPosition(position: SettingsLog.Position) {
    updateBranchPosition(CLOUD_REF_NAME, position)
  }

  override fun setMasterPosition(position: SettingsLog.Position) {
    updateBranchPosition(MASTER_REF_NAME, position)
  }

  private fun updateBranchPosition(refName: String, targetPosition: SettingsLog.Position): Ref {
    val ref = repository.findRef(refName)!!
    val previousObjectId = ref.objectId
    val result = repository.updateRef(ref.name).apply {
      setNewObjectId(ObjectId.fromString(targetPosition.id))
      isForceUpdate = true
    }.update()
    LOG.info("Updated position of ${ref.short} from ${previousObjectId.short} to $targetPosition: $result")

    // updateRef() doesn't change the working tree, it only moves the label
    // Therefore, after we move the refName to a new position, then some local changes can appear,
    // because the working tree is now compared with another head.
    // We don't want these local changes => we reset the working copy to the state of the current head.
    git.reset().setMode(ResetCommand.ResetType.HARD).call()

    return repository.findRef(ref.name)!!
  }

  override fun advanceMaster(): SettingsLog.Position {
    git.checkout().setName(MASTER_REF_NAME).call()
    LOG.info("Advancing master@${master.objectId.short}. Need merge of ide@${ide.objectId.short} and cloud@${cloud.objectId.short}")
    // 1. move master to ide
    git.reset().setRef(IDE_REF_NAME).setMode(ResetCommand.ResetType.HARD).call()

    // 2. merge with cloud
    val mergeResult = git.merge().include(cloud).call()
    LOG.info("Merge of master&ide@${master.objectId.short} with cloud@${cloud.objectId.short}: $mergeResult")
    if (mergeResult.mergeStatus == CONFLICTING) {
      LOG.info("Merge of master&ide with cloud failed with conflicts. Aborting and merging with simplified last-modified strategy...")
      abortMerge()

      // todo implement more precise last-modified-per-file-strategy: use the version of the file which was
      // current implementation take the whole YOURS or OTHERS subtree based on the date of the latest commit in ide and cloud branches:
      // e.g. if the latest commit was made to 'cloud', then 'cloud' (which is OTHERS for this merge) will be used as the source of truth.
      mergeUsingSimplifiedLastModifiedStrategy()
    }
    // todo check other statuses and force consistency if needed
    return getPosition(master)
  }

  private fun mergeUsingSimplifiedLastModifiedStrategy() {
    val ideLastDate = getDate(getBranchTip(ide))
    val cloudLastDate = getDate(getBranchTip(cloud))
    val mergeStrategy = if (ideLastDate >= cloudLastDate) MergeStrategy.OURS else MergeStrategy.THEIRS
    val mergeResult = git.merge().include(cloud).setStrategy(mergeStrategy).call()
    LOG.info("Merging with the last-modified strategy completed with result: $mergeResult")
  }

  private fun getBranchTip(ref: Ref): RevCommit = git.log().add(ref.objectId).setMaxCount(1).call().first()

  private fun getDate(commit: RevCommit): Instant {
    try {
      val noteObject = git.notesShow().setObjectId(commit).call()
      if (noteObject != null) {
        val noteContent = String(repository.open(noteObject.data).bytes, StandardCharsets.UTF_8)
        val matcher = DATE_PATTERN.matcher(noteContent)
        if (matcher.matches()) {
          val date = matcher.group(1)
          return Instant.ofEpochMilli(date.toLong())
        }
        else {
          LOG.warn("Note for commit $commit doesn't match format: [$noteContent]")
        }
      }
      else {
        if (commit.parentCount == 1) { // merge happens locally, so the commit time is accurate enough, no note is needed
          LOG.warn("No note assigned to commit $commit")
        }
      }
    }
    catch (e: Throwable) {
      LOG.warn("Error reading a note assigned to commit $commit", e)
    }
    return Instant.ofEpochSecond(commit.commitTime.toLong())
  }

  private fun abortMerge() {
    repository.writeMergeCommitMsg(null)
    repository.writeMergeHeads(null)
    git.reset().setMode(ResetCommand.ResetType.HARD).call()
  }

  private fun Repository.headCommit(): RevCommit {
    val ref = this.findRef(Constants.HEAD)
    return this.parseCommit(ref.objectId)
  }

  private val Ref.short get() = this.name.removePrefix(R_HEADS)

  private val ObjectId.short get() = this.name.substring(0, 8)

  private data class BranchPosition(override val id: String) : SettingsLog.Position {
    override fun toString(): String = id.substring(0, 8)
  }

  private companion object {
    val LOG = logger<GitSettingsLog>()

    const val MASTER_REF_NAME = "master"
    const val IDE_REF_NAME = "ide"
    const val CLOUD_REF_NAME = "cloud"

    const val PLUGINS_FILE = "plugins.json"
    const val METAINFO_FOLDER = ".metainfo"

    const val DATE_PREFIX = "date: "
    val DATE_PATTERN = Pattern.compile("$DATE_PREFIX(\\d+)")
  }
}
