package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.SettingsSnapshotZipSerializer.deserializeSettingsProviders
import com.intellij.settingsSync.SettingsSnapshotZipSerializer.serializeSettingsProviders
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.settingsSync.plugins.SettingsSyncPluginsStateMerger.mergePluginStates
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.io.createFile
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
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.treewalk.TreeWalk
import org.jetbrains.annotations.VisibleForTesting
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.io.path.div
import kotlin.io.path.exists


internal class GitSettingsLog(private val settingsSyncStorage: Path,
                              private val rootConfigPath: Path,
                              parentDisposable: Disposable,
                              private val userDataProvider: () -> JBAccountInfoService.JBAData?,
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
    configureJGit()

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

  private fun configureJGit() {
    GpgSigner.setDefault(MockGpgSigner())
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
    commit("Initial", allowEmpty = false)
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
    if (snapshot.isEmpty()) {
      if (warnAboutEmptySnapshot) {
        LOG.error("Empty snapshot, requested to apply on branch '$refName' with message '$message'")
      }
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
      writeFileStateContent(fileState, file)
      addCommand.addFilepattern(fileState.file)
    }

    for (additionalFile in snapshot.additionalFiles) {
      val file = settingsSyncStorage.resolve(METAINFO_FOLDER).resolve(additionalFile.file)
      writeFileStateContent(additionalFile, file)
      addCommand.addFilepattern("$METAINFO_FOLDER/${additionalFile.file}")
    }

    if (snapshot.plugins != null) {
      val sortedState = SettingsSyncPluginsState(snapshot.plugins.plugins.toSortedMap())
      val pluginsState = json.encodeToString(sortedState)
      pluginsFile.write(pluginsState)
      addCommand.addFilepattern("$METAINFO_FOLDER/$PLUGINS_FILE")
    }

    for ((relativePath, content) in serializeSettingsProviders(snapshot.settingsFromProviders)) {
      settingsSyncStorage.resolve(METAINFO_FOLDER).resolve(relativePath).write(content)
      addCommand.addFilepattern("$METAINFO_FOLDER/$relativePath")
    }

    addCommand.call()

    val info = snapshot.metaInfo.appInfo
    val body = if (info != null) {
      val thisOrThat = if (info.applicationId == SettingsSyncLocalSettings.getInstance().applicationId) "[this]" else "[other]"
      "\n\n" + """
        id:     $thisOrThat ${info.applicationId}
        build:  ${info.buildNumber}
        user:   ${info.userName}
        host:   ${info.hostName}
        config: ${info.configFolder}
      """.trimIndent()
    }
    else {
      ""
    }
    commit("$message$body", snapshot.metaInfo.dateCreated, allowEmpty = false)
  }

  private fun writeFileStateContent(fileState: FileState, fileToWrite: Path) {
    when (fileState) {
      is FileState.Modified -> fileToWrite.write(fileState.content)
      is FileState.Deleted -> fileToWrite.write(DELETED_FILE_MARKER)
    }
  }

  private fun commit(message: String, dateCreated: Instant? = null, allowEmpty: Boolean) {
    try {
      // Don't allow empty commit: sometimes the stream provider can notify about changes but there are no actual changes on disk
      val mockGpgConfig = GpgConfig("", GpgConfig.GpgFormat.OPENPGP, "")
      val commitData = git.commit()
        .setMessage(message)
        .setAllowEmpty(allowEmpty)
        .setNoVerify(true)
        .setSign(false)
        .setGpgConfig(mockGpgConfig)

      userDataProvider()?.let {
        commitData.author = PersonIdent(it.loginName, it.email)
      }
      val commit = commitData.call()

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

    val settingFiles = settingsSyncStorage.toFile().walkTopDown()
      .onEnter { it.name != ".git" && it.name != METAINFO_FOLDER }
      .filter { it.isFile && it.name != ".gitignore" }
      .mapTo(HashSet()) { getFileStateFromFileWithDeletedMarker(it.toPath(), settingsSyncStorage) }

    val metaInfoFolder = settingsSyncStorage.resolve(METAINFO_FOLDER)

    val (settingsFromProviders, filesFromProviders) = deserializeSettingsProviders(metaInfoFolder)

    val additionalFiles = metaInfoFolder.toFile().walkTopDown()
      .filter { it.isFile && it.name != PLUGINS_FILE && !filesFromProviders.contains(it.toPath()) }
      .mapTo(HashSet()) { getFileStateFromFileWithDeletedMarker(it.toPath(), metaInfoFolder) }

    val pluginsState = readPluginsState()
    return SettingsSnapshot(MetaInfo(lastModifiedDate, getLocalApplicationInfo()),
                            settingFiles, pluginsState, settingsFromProviders, additionalFiles)
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
      val conflictingFiles = mergeResult.conflicts.keys.toMutableList()
      LOG.info("Merge of master&ide with cloud failed with conflicts in files: ${conflictingFiles}")

      val ideBranchTip = getBranchTip(ide)
      val cloudBranchTip = getBranchTip(cloud)

      val addCommand = git.add()
      val pluginJsonPath = conflictingFiles.find { it == "$METAINFO_FOLDER/$PLUGINS_FILE" }
      if (pluginJsonPath != null) {
        val mergedContent = mergePluginJson(pluginJsonPath, ideBranchTip, cloudBranchTip)
        pluginsFile.write(mergedContent)
        addCommand.addFilepattern(pluginJsonPath)
        conflictingFiles -= pluginJsonPath
      }

      SettingsProvider.SETTINGS_PROVIDER_EP.forEachExtensionSafe(Consumer {
        val relativePath = "$METAINFO_FOLDER/${it.id}/${it.fileName}"
        val file = settingsSyncStorage.resolve(relativePath)
        if (file.exists() && conflictingFiles.contains(relativePath)) {
          val mergedContent = mergeSettingsProviderFile(it, relativePath, ideBranchTip, cloudBranchTip)
          file.write(mergedContent)
          addCommand.addFilepattern(relativePath)
          conflictingFiles -= relativePath
        }
      })

      mergeFilesOneByOne(conflictingFiles, ideBranchTip, cloudBranchTip)
      for (file in conflictingFiles) {
        addCommand.addFilepattern(file)
      }
      addCommand.call()

      commit("Merge with conflicts", allowEmpty = true)
    }
    // todo check other statuses and force consistency if needed
    return getPosition(master)
  }

  private fun <T : Any> mergeSettingsProviderFile(settingsProvider: SettingsProvider<T>,
                                                  relativePath: String,
                                                  ideBranchTip: RevCommit,
                                                  cloudBranchTip: RevCommit): String {
    return smartMergeFile(relativePath, ideBranchTip, cloudBranchTip,
                          deserializer = { settingsProvider.deserialize(it) },
                          serializer = { settingsProvider.serialize(it) },
                          merger = { base: T?, cloud: T, ide: T ->
                            settingsProvider.mergeStates(base, cloud, ide)
                          })
  }

  private fun mergePluginJson(pluginJson: String, ideBranchTip: RevCommit, cloudBranchTip: RevCommit): String {
    return smartMergeFile(pluginJson, ideBranchTip, cloudBranchTip,
                          deserializer = { json.decodeFromString<SettingsSyncPluginsState>(it) },
                          serializer = { json.encodeToString(it) },
                          merger = { base, cloud, ide -> mergePluginStates(base ?: SettingsSyncPluginsState(emptyMap()), cloud, ide) })
  }

  private fun <T> smartMergeFile(
    relativePath: String,
    ideBranchTip: RevCommit,
    cloudBranchTip: RevCommit,
    serializer: (T) -> String,
    deserializer: (String) -> T,
    merger: (T?, T, T) -> T): String
  {
    val ideContent = getFileContentInBranch(relativePath, ideBranchTip)
    val ideState = deserializer(ideContent)
    val cloudContent = getFileContentInBranch(relativePath, cloudBranchTip)
    val cloudState = deserializer(cloudContent)
    val mergeBaseContent = findMergeBaseContent(relativePath, ideBranchTip, cloudBranchTip)
    val baseState = if (mergeBaseContent != null)
      deserializer(mergeBaseContent)
    else null

    val mergedState = merger(baseState, cloudState, ideState)
    return serializer(mergedState)
  }

  private fun findMergeBaseContent(pluginJson: String, commit1: RevCommit, commit2: RevCommit): String? {
    val mergeBase = try {
      findMergeBase(commit1, commit2)
    }
    catch (e: Exception) {
      LOG.warn("Couldn't find the merge base for $pluginJson between $commit1 and $commit2", e)
      null
    }
    if (mergeBase == null) {
      return null
    }
    return getFileContentInBranch(pluginJson, mergeBase)
  }

  private fun findMergeBase(commit1: RevCommit, commit2: RevCommit): RevCommit? {
    RevWalk(repository).use { walk ->
      walk.revFilter = RevFilter.MERGE_BASE
      walk.markStart(repository.parseCommit(commit1.toObjectId()))
      walk.markStart(repository.parseCommit(commit2.toObjectId()))
      return walk.next()
    }
  }

  private fun getFileContentInBranch(file: String, branchTip: RevCommit): String {
    TreeWalk.forPath(repository, file, branchTip.tree).use { treeWalk ->
      val blobId = treeWalk.getObjectId(0)
      repository.newObjectReader().use { objectReader ->
        val objectLoader = objectReader.open(blobId)
        return String(objectLoader.bytes, StandardCharsets.UTF_8)
      }
    }
  }

  private fun mergeFilesOneByOne(conflictingFiles: Collection<String>, ideTip: RevCommit, cloudTip: RevCommit) {
    if (conflictingFiles.isEmpty()) {
      return
    }

    val ideTipDate = getDate(ideTip)
    val cloudTipDate = getDate(cloudTip)
    for (file in conflictingFiles) {
      val ideTipForFile = getLatestCommitForFile(file, ide)
      val ideDateForFile = if (ideTipForFile != null) getDate(ideTipForFile) else ideTipDate
      val cloudTipForFile = getLatestCommitForFile(file, cloud)
      val cloudDateForFile = if (cloudTipForFile != null) getDate(cloudTipForFile) else cloudTipDate

      val content = if (ideDateForFile >= cloudDateForFile) {
        LOG.info("File $file was modified later in 'ide' in ${ideTipForFile?.short ?: ideTip.short}")
        getFileContentInBranch(file, ideTip)
      }
      else {
        LOG.info("File $file was modified later in 'cloud' in ${cloudTipForFile?.short ?: cloudTip.short}")
        getFileContentInBranch(file, cloudTip)
      }

      settingsSyncStorage.resolve(file).write(content)
    }
  }

  private fun getLatestCommitForFile(filePath: String, branch: Ref): RevCommit? {
    val commit = git.log()
      .add(branch.objectId)
      .setMaxCount(1)
      .addPath(filePath)
      .call()
      .firstOrNull()

    if (commit == null) {
      LOG.warn("Could not find latest commit for file $filePath in branch $branch")
    }
    return commit
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

  class MockGpgSigner : GpgSigner() {
    override fun sign(commit: CommitBuilder?, gpgSigningKey: String?, committer: PersonIdent?, credentialsProvider: CredentialsProvider?) {}

    override fun canLocateSigningKey(gpgSigningKey: String?, committer: PersonIdent?, credentialsProvider: CredentialsProvider?): Boolean {
      return false
    }
  }
}

internal fun Repository.headCommit(): RevCommit {
  val ref = this.findRef(Constants.HEAD)
  return this.parseCommit(ref.objectId)
}
