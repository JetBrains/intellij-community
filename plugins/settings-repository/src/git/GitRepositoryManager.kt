// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.io.*
import kotlinx.coroutines.ensureActive
import org.eclipse.jgit.api.AddCommand
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.api.errors.UnmergedPathsException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.ignore.IgnoreNode
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.*
import org.jetbrains.settingsRepository.*
import org.jetbrains.settingsRepository.RepositoryManager.Updater
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.write
import kotlin.coroutines.coroutineContext
import kotlin.io.path.exists

class GitRepositoryManager(private val credentialsStore: Lazy<IcsCredentialsStore>,
                           dir: Path,
                           parentDisposable: Disposable) : BaseRepositoryManager(dir), GitRepositoryClient, Disposable {

  init {
    Disposer.register(parentDisposable, this)
  }

  override val repository: Repository
    get() {
      var r = _repository
      if (r == null) {
        r = buildRepository(workTree = dir)
        _repository = r
        if (ApplicationManager.getApplication()?.isUnitTestMode != true) {
          ShutDownTracker.getInstance().registerShutdownTask { _repository?.close() }
        }
      }
      return r
    }

  // we must recreate repository if dir changed because repository stores old state and cannot be reinitialized (so, old instance cannot be reused and we must instantiate new one)
  private var _repository: Repository? = null

  override val credentialsProvider: CredentialsProvider by lazy {
    JGitCredentialsProvider(credentialsStore, repository)
  }

  override fun dispose() {
    _repository?.close()
  }

  private var ignoreRules: IgnoreNode? = null

  override fun createRepositoryIfNeeded(): Boolean {
    ignoreRules = null

    if (isRepositoryExists()) {
      return false
    }

    repository.create()
    repository.disableAutoCrLf()
    return true
  }

  override fun deleteRepository() {
    ignoreRules = null

    try {
      super.deleteRepository()
    }
    finally {
      val r = _repository
      if (r != null) {
        _repository = null
        r.close()
      }
    }
  }

  override fun getUpstream() = repository.upstream

  override fun setUpstream(url: String?, branch: String?) {
    repository.setUpstream(url, branch ?: Constants.MASTER)
  }

  override fun isRepositoryExists(): Boolean {
    val repo = _repository
    if (repo == null) {
      return Files.exists(dir) && FileRepositoryBuilder().setWorkTree(dir.toFile()).setUseSystemConfig(false).setup().objectDirectory.exists()
    }
    else {
      return repo.objectDatabase.exists()
    }
  }

  override fun hasUpstream() = getUpstream() != null

  override fun addToIndex(file: Path, path: String, content: ByteArray) {
    repository.edit(AddLoadedFile(path, content, file.lastModified().toMillis()))
  }

  override fun deleteFromIndex(path: String, isFile: Boolean) {
    repository.deletePath(path, isFile, false)
  }

  override suspend fun commit(syncType: SyncType?, fixStateIfCannotCommit: Boolean): Boolean {
    lock.write {
      try {
        // will be reset if OVERWRITE_LOCAL, so, we should not fix state in this case
        return commitIfCan(if (!fixStateIfCannotCommit || syncType == SyncType.OVERWRITE_LOCAL) repository.repositoryState else repository.fixAndGetState())
      }
      catch (e: UnmergedPathsException) {
        if (syncType == SyncType.OVERWRITE_LOCAL) {
          LOG.warn("Unmerged detected, ignored because sync type is OVERWRITE_LOCAL", e)
          return false
        }
        else {
          coroutineContext.ensureActive()
          LOG.warn("Unmerged detected, will be attempted to resolve", e)
          resolveUnmergedConflicts(repository)
          coroutineContext.ensureActive()
          return commitIfCan(repository.fixAndGetState())
        }
      }
      catch (e: NoHeadException) {
        LOG.warn("Cannot commit - no HEAD", e)
        return false
      }
    }
  }

  private suspend fun commitIfCan(state: RepositoryState): Boolean {
    if (state.canCommit()) {
      return commit(repository)
    }
    else {
      LOG.warn("Cannot commit, repository in state ${state.description}")
      return false
    }
  }

  override fun getAheadCommitsCount() = repository.getAheadCommitsCount()

  override suspend fun push() {
    LOG.debug("Push")

    val refSpecs = SmartList(RemoteConfig(repository.config, Constants.DEFAULT_REMOTE_NAME).pushRefSpecs)
    if (refSpecs.isEmpty()) {
      val head = repository.findRef(Constants.HEAD)
      if (head != null && head.isSymbolic) {
        refSpecs.add(RefSpec(head.leaf.name))
      }
    }

    val monitor = progressMonitor()
    for (transport in Transport.openAll(repository, Constants.DEFAULT_REMOTE_NAME, Transport.Operation.PUSH)) {
      for (attempt in 0..1) {
        transport.credentialsProvider = credentialsProvider
        try {
          val result = blockingContext {
            transport.push(monitor, transport.findRemoteRefUpdatesFor(refSpecs))
          }
          if (LOG.isDebugEnabled) {
            printMessages(result)

            for (refUpdate in result.remoteUpdates) {
              LOG.debug(refUpdate.toString())
            }
          }
          break
        }
        catch (e: TransportException) {
          if (e.status == TransportException.Status.NOT_PERMITTED) {
            if (attempt == 0) {
              credentialsProvider.reset(transport.uri)
            }
            else {
              throw AuthenticationException(e)
            }
          }
          else if (e.status == TransportException.Status.BAD_GATEWAY) {
            continue
          }
          else {
            wrapIfNeedAndReThrow(e)
          }
        }
        finally {
          transport.close()
        }
      }
    }
  }

  override suspend fun fetch(): Updater {
    val pullTask = Pull(this)
    val refToMerge = pullTask.fetch()
    return object : Updater {
      override var definitelySkipPush = false

      // KT-8632
      override suspend fun merge(): UpdateResult? = lock.write {
        val committed = commit()
        if (refToMerge == null) {
          definitelySkipPush = !committed && getAheadCommitsCount() == 0
          return null
        }
        return pullTask.pull(prefetchedRefToMerge = refToMerge)
      }
    }
  }

  override suspend fun pull() = Pull(this).pull()

  override suspend fun resetToTheirs() = Reset(this).reset(true)

  override suspend fun resetToMy(localRepositoryInitializer: (() -> Unit)?) = Reset(this).reset(false, localRepositoryInitializer)

  override fun canCommit() = repository.repositoryState.canCommit()

  fun renameDirectory(pairs: Map<String, String?>, commitMessage: String): Boolean {
    var addCommand: AddCommand? = null
    val toDelete = SmartList<DeleteDirectory>()
    for ((oldPath, newPath) in pairs) {
      val old = dir.resolve(oldPath)
      if (!old.exists()) {
        continue
      }

      LOG.info("Rename $oldPath to $newPath")
      old.directoryStreamIfExists {
        val new = if (newPath == null) dir else dir.resolve(newPath)
        for (file in it) {
          LOG.runAndLogException {
            if (file.isHidden()) {
              file.delete()
            }
            else {
              try {
                file.move(new.resolve(file.fileName))
              }
              catch (ignored: FileAlreadyExistsException) {
                return@runAndLogException
              }

              if (addCommand == null) {
                addCommand = AddCommand(repository)
              }
              addCommand!!.addFilepattern(if (newPath == null) file.fileName.toString() else "$newPath/${file.fileName}")
            }
          }
        }
        toDelete.add(DeleteDirectory(oldPath))
      }

      LOG.runAndLogException {
        old.delete()
      }
    }

    if (toDelete.isEmpty() && addCommand == null) {
      return false
    }

    repository.edit(toDelete)
    addCommand?.call()

    repository.commit(IdeaCommitMessageFormatter().appendCommitOwnerInfo().append(commitMessage).toString())
    return true
  }

  private fun getIgnoreRules(): IgnoreNode? {
    var node = ignoreRules
    if (node == null) {
      val file = dir.resolve(Constants.DOT_GIT_IGNORE)
      if (file.exists()) {
        node = IgnoreNode()
        file.inputStream().use { node.parse(it) }
        ignoreRules = node
      }
    }
    return node
  }

  override fun isPathIgnored(path: String): Boolean {
    // add first slash as WorkingTreeIterator does "The ignore code wants path to start with a '/' if possible."
    return getIgnoreRules()?.isIgnored("/$path", false) == IgnoreNode.MatchResult.IGNORED
  }
}

fun printMessages(fetchResult: OperationResult) {
  if (LOG.isDebugEnabled) {
    val messages = fetchResult.messages
    if (!StringUtil.isEmptyOrSpaces(messages)) {
      LOG.debug(messages)
    }
  }
}

class GitRepositoryService : RepositoryService {
  override fun isValidRepository(file: Path): Boolean {
    if (file.resolve(Constants.DOT_GIT).exists()) {
      return true
    }

    // existing bare repository
    try {
      buildRepository(gitDir = file, mustExists = true)
    }
    catch (e: IOException) {
      return false
    }

    return true
  }
}