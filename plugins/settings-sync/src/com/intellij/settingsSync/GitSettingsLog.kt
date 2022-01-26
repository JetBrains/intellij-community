package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.createFile
import com.intellij.util.io.exists
import com.intellij.util.io.isFile
import com.intellij.util.io.write
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING
import org.eclipse.jgit.api.MergeResult.MergeStatus.FAST_FORWARD
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Constants.R_HEADS
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.relativeTo

internal class GitSettingsLog(private val settingsSyncStorage: Path,
                              private val rootConfigPath: Path,
                              parentDisposable: Disposable,
                              private val collectFilesToExportFromSettings: () -> Collection<Path>
) : SettingsLog, Disposable {

  private lateinit var repository: Repository
  private lateinit var git: Git

  private val master: Ref get() = repository.findRef(MASTER_REF_NAME)!!
  private val ide: Ref get() = repository.findRef(IDE_REF_NAME)!!
  private val cloud: Ref get() = repository.findRef(CLOUD_REF_NAME)!!

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun initialize(): Boolean {
    val dotGit = settingsSyncStorage.resolve(".git")
    repository = FileRepositoryBuilder.create(dotGit.toFile())
    git = Git(repository)

    val newRepository = !dotGit.exists()
    if (newRepository) {
      LOG.info("Initializing new Git repository for Settings Sync at $settingsSyncStorage")
      repository.create()
      initRepository(repository)
      copyExistingSettings(repository)
    }

    createBranchIfNeeded(MASTER_REF_NAME, newRepository)
    createBranchIfNeeded(CLOUD_REF_NAME, newRepository)
    createBranchIfNeeded(IDE_REF_NAME, newRepository)

    return newRepository
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

  private fun copyExistingSettings(repository: Repository) {
    LOG.info("Copying existing settings from $rootConfigPath to $settingsSyncStorage")
    val copiedFileSpecs = mutableListOf<String>()

    val filesToExport = collectFilesToExportFromSettings()
    for (path in filesToExport) {
      val fileSpec = path.relativeTo(rootConfigPath).toString()
      if (path.isFile()) {
        // 'path' is e.g. 'ROOT_CONFIG/options/editor.xml'
        val target = settingsSyncStorage.resolve(fileSpec)
        NioFiles.createDirectories(target.parent)
        Files.copy(path, target, LinkOption.NOFOLLOW_LINKS)
      }
      else {
        // 'path' is e.g. 'ROOT_CONFIG/keymaps/'
        copyDirectory(path, settingsSyncStorage)
      }
      copiedFileSpecs.add(fileSpec)
    }

    if (copiedFileSpecs.isNotEmpty()) {
      val git = Git(repository)
      val addCommand = git.add()
      for (fileSpec in copiedFileSpecs) {
        addCommand.addFilepattern(fileSpec)
      }
      addCommand.call()
      git.commit().setMessage("Copy existing configs").call()
    }
  }

  private fun copyDirectory(dirToCopy: Path, targetDir: Path) {
    Files.walkFileTree(dirToCopy, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val target = targetDir.resolve(dirToCopy.parent.relativize(file))  // file is mykeymap.xml => target is keymaps/mykeymap.xml
        NioFiles.createDirectories(target.parent)
        Files.copy(file, target, LinkOption.NOFOLLOW_LINKS)
        return FileVisitResult.CONTINUE
      }
    })
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
    git.commit().setMessage("Initial").call()
  }

  override fun applyIdeState(snapshot: SettingsSnapshot) {
    applyState(IDE_REF_NAME, snapshot)
  }

  override fun applyCloudState(snapshot: SettingsSnapshot) {
    applyState(CLOUD_REF_NAME, snapshot)
  }

  private fun applyState(refName: String, snapshot: SettingsSnapshot) {
    if (snapshot.isEmpty()) {
      LOG.error("Empty snapshot")
      return
    }

    git.checkout().setName(refName).call()
    applySnapshotAndCommit(refName, snapshot)
  }

  private fun applySnapshotAndCommit(refName: String, snapshot: SettingsSnapshot) {
    // todo check repository consistency before each operation: that we're on master, that rb is deleted, that there're no uncommitted changes

    LOG.info("Applying settings changes to branch $refName")
    val addCommand = git.add()
    val message = "Apply changes received from $refName"
    for (fileState in snapshot.fileStates) {
      val file = settingsSyncStorage.resolve(fileState.file)
      when (fileState) {
        is FileState.Modified -> file.write(fileState.content, 0, fileState.size)
        is FileState.Deleted -> file.write(DELETED_FILE_MARKER)
      }
      addCommand.addFilepattern(fileState.file)
    }
    addCommand.call()
    // Don't allow empty commit: sometimes the stream provider can notify about changes but there are no actual changes on disk
    try {
      git.commit().setMessage(message).setAllowEmpty(false).call()
    }
    catch (e: EmptyCommitException) {
      LOG.info("No actual changes in the settings")
    }
  }

  override fun dispose() {
    repository.close()   // todo synchronize
  }

  override fun collectCurrentSnapshot(): SettingsSnapshot {
    // todo check repository consistency, e.g. there should be no uncommitted changes

    val files = settingsSyncStorage.toFile().walkTopDown()
      .onEnter { it.name != ".git" }
      .filter { it.isFile && it.name != ".gitignore" }
      .mapTo(HashSet()) { getFileStateFromFileWithDeletedMarker(it.toPath(), settingsSyncStorage) }
    return SettingsSnapshot(files)
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

  private fun updateBranchPosition(refName: String, targetPosition: SettingsLog.Position): Ref {
    val ref = repository.findRef(refName)!!
    val previousObjectId = ref.objectId
    val targetObjectId = ObjectId.fromString(targetPosition.id)
    val refUpdate = repository.updateRef(ref.name)
    refUpdate.setNewObjectId(targetObjectId)
    val result = refUpdate.update()
    LOG.info("Updated position of ${ref.short} from ${previousObjectId.short} to $targetPosition: $result")
    return repository.findRef(ref.name)!!
  }

  private fun fastForwardMaster(branchOnSamePosition: Ref, targetBranch: Ref): BranchPosition {
      LOG.info("Advancing master. Its position is equal to ${branchOnSamePosition.short}: ${master.objectId.short}. " +
               "Fast-forwarding to ${targetBranch.short} ${targetBranch.objectId.short}")
      val mergeResult = git.merge().include(targetBranch).call()
      if (mergeResult.mergeStatus != FAST_FORWARD) {
        LOG.warn("Non-fast-forward result: $mergeResult")
        // todo check consistency here
      }
      return getPosition(master)
  }

  override fun advanceMaster(): SettingsLog.Position {
    git.checkout().setName(MASTER_REF_NAME).call()

    if (master.objectId == ide.objectId) {
      return fastForwardMaster(ide, cloud)
    }

    if (master.objectId == cloud.objectId) {
      return fastForwardMaster(cloud, ide)
    }

    LOG.info("Advancing master@${master.objectId.short}. Need merge of ide@${ide.objectId.short} and cloud@${cloud.objectId.short}")
    // 1. move master to ide
    git.reset().setRef(IDE_REF_NAME).setMode(ResetCommand.ResetType.HARD).call();

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
    val ideTip = git.log().add(ide.objectId).setMaxCount(1).call().first()
    val ideLastDate = ideTip.commitTime
    val cloudTip = git.log().add(cloud.objectId).setMaxCount(1).call().first()
    val cloudLastDate = cloudTip.commitTime
    val mergeStrategy = if (ideLastDate >= cloudLastDate) MergeStrategy.OURS else MergeStrategy.THEIRS
    val mergeResult = git.merge().include(cloud).setStrategy(mergeStrategy).call()
    LOG.info("Merging with the last-modified strategy completed with result: $mergeResult")
  }

  private fun abortMerge() {
    repository.writeMergeCommitMsg(null);
    repository.writeMergeHeads(null);
    git.reset().setMode(ResetCommand.ResetType.HARD).call();
  }

  private fun Repository.headCommit(): RevCommit {
    val ref = this.findRef(Constants.HEAD)
    return this.parseCommit(ref.objectId)
  }

  private val Ref.short get() = this.name.removePrefix(R_HEADS)

  private val ObjectId.short get() = this.name.substring(0, 8)

  private data class BranchPosition(override val id: String): SettingsLog.Position {
    override fun toString(): String = id.substring(0, 8)
  }

  private companion object {
    val LOG = logger<GitSettingsLog>()

    const val MASTER_REF_NAME = "master"
    const val IDE_REF_NAME = "ide"
    const val CLOUD_REF_NAME = "cloud"
  }
}