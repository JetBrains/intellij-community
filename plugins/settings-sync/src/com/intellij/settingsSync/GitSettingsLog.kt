package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult.MergeStatus.*
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
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
  private lateinit var remoteBranch: RevCommit

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun initialize(): Boolean {
    val dotGit = settingsSyncStorage.resolve(".git")
    repository = FileRepositoryBuilder.create(dotGit.toFile())

    val newRepository = !dotGit.exists()
    if (newRepository) {
      repository.create()
      initRepository(repository)
      copyExistingSettings(repository)
    }

    remoteBranch = repository.headCommit()

    return newRepository
  }

  private fun copyExistingSettings(repository: Repository) {
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
      git.commit().setMessage("copy existing configs").call()
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
    git.commit().setMessage("initial").call()
    // todo repo should be pushed after initialization (or be initialized by data received from the server)
  }

  @RequiresBackgroundThread
  override fun applyLocalState(snapshot: SettingsSnapshot) {
    if (snapshot.fileStates.isEmpty()) { // todo move upwards?
      LOG.error("Don't record empty settings snapshot")
    }

    applySnapshotAndCommit(snapshot)
  }

  private fun applySnapshotAndCommit(snapshot: SettingsSnapshot): RevCommit {
    // todo check repository consistency before each operation: that we're on master, that rb is deleted, that there're no uncommitted changes

    // todo recording local operation should also be merged instead of applying,
    // because of a race: local changes can happen after receiving changes from server but before and applying them.

    val git = Git(repository)
    val addCommand = git.add()
    val message = StringBuilder()
    for (fileState in snapshot.fileStates) {
      settingsSyncStorage.resolve(fileState.file).write(fileState.content, 0, fileState.size)
      addCommand.addFilepattern(fileState.file)
      message.appendLine(fileState.file)
    }
    addCommand.call()
    return git.commit().setMessage("committing $message").call()
  }

  override fun pushedSuccessfully() {
    remoteBranch = repository.headCommit()
  }

  override fun applyRemoteState(snapshot: SettingsSnapshot): Boolean { // todo improve return result API
    // todo check repository consistency before each operation: that we're on master, than rb is deleted, that there're no uncommitted changes

    val git = Git(repository)

    if (repository.resolve("rb") != null) {
      LOG.warn("rb wasn't deleted") // todo maybe don't need a warning here: it can happen after abnormal termination
      git.checkout().setName("master").call() // todo check if not already checked out
      git.branchDelete().setBranchNames("rb").setForce(true).call()
    }

    try {
      var rb = git.checkout().setCreateBranch(true).setName("rb").setStartPoint(remoteBranch).call().objectId
      rb = applySnapshotAndCommit(snapshot)
      git.checkout().setName("master").call()
      val mergeResult = git.merge().include(rb).call()

      // todo handle merge conflicts

      when (mergeResult.mergeStatus) {
        FAST_FORWARD, ALREADY_UP_TO_DATE -> {
          return false
        }
        MERGED -> {
          return true
        }
        else -> {
          throw IllegalStateException("Unexpected merge result status: " + mergeResult.mergeStatus) //todo
        }
      }
    }
    finally {
      try {
        git.checkout().setName("master").call() // todo extract to "returnToConsistency"
        git.branchDelete().setBranchNames("rb").setForce(true).call()
      }
      catch (e: Throwable) {
        LOG.error("Couldn't return to consistent state", e)
      }
    }
  }

  override fun dispose() {
    repository.close()   // todo synchronize
  }

  override fun collectCurrentSnapshot(): SettingsSnapshot {   // todo check if there are uncommitted changes (should be none)
    val files = settingsSyncStorage.toFile().walkTopDown()
      .onEnter { it.name != ".git" }
      .filter { it.isFile && it.name != ".gitignore" }
      .mapTo(HashSet()) {
        val relative = it.toPath().relativeTo(settingsSyncStorage)
        FileState(relative.toString(), it.toPath().readBytes(), it.toPath().size().toInt())
      }
    return SettingsSnapshot(files)
  }

  private fun Repository.headCommit(): RevCommit {
    val ref = this.exactRef(Constants.HEAD)
    return this.parseCommit(ref.objectId)
  }

  companion object {
    val LOG = logger<GitSettingsLog>()
  }
}