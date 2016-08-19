/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.*
import org.eclipse.jgit.api.AddCommand
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.api.errors.UnmergedPathsException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.ignore.IgnoreNode
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.*
import org.jetbrains.jgit.dirCache.AddLoadedFile
import org.jetbrains.jgit.dirCache.DeleteDirectory
import org.jetbrains.jgit.dirCache.deletePath
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.settingsRepository.*
import org.jetbrains.settingsRepository.RepositoryManager.Updater
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.write

class GitRepositoryManager(private val credentialsStore: NotNullLazyValue<CredentialsStore>, dir: Path) : BaseRepositoryManager(dir) {
  val repository: Repository
    get() {
      var r = _repository
      if (r == null) {
        r = FileRepositoryBuilder().setWorkTree(dir.toFile()).build()
        _repository = r
        if (ApplicationManager.getApplication()?.isUnitTestMode != true) {
          ShutDownTracker.getInstance().registerShutdownTask { _repository?.close() }
        }
      }
      return r!!
    }

  // we must recreate repository if dir changed because repository stores old state and cannot be reinitialized (so, old instance cannot be reused and we must instantiate new one)
  var _repository: Repository? = null

  val credentialsProvider: CredentialsProvider by lazy {
    JGitCredentialsProvider(credentialsStore, repository)
  }

  private var ignoreRules: IgnoreNode? = null

  override fun createRepositoryIfNeed(): Boolean {
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

    super.deleteRepository()

    val r = _repository
    if (r != null) {
      _repository = null
      r.close()
    }
  }

  override fun getUpstream(): String? {
    return repository.config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL).nullize()
  }

  override fun setUpstream(url: String?, branch: String?) {
    repository.setUpstream(url, branch ?: Constants.MASTER)
  }

  override fun isRepositoryExists(): Boolean {
    val repo = _repository
    if (repo == null) {
      return dir.exists() && FileRepositoryBuilder().setWorkTree(dir.toFile()).setup().objectDirectory.exists()
    }
    else {
      return repo.objectDatabase.exists()
    }
  }

  override fun hasUpstream() = getUpstream() != null

  override fun addToIndex(file: Path, path: String, content: ByteArray, size: Int) {
    repository.edit(AddLoadedFile(path, content, size, file.lastModified().toMillis()))
  }

  override fun deleteFromIndex(path: String, isFile: Boolean) {
    repository.deletePath(path, isFile, false)
  }

  override fun commit(indicator: ProgressIndicator?, syncType: SyncType?, fixStateIfCannotCommit: Boolean): Boolean {
    lock.write {
      try {
        // will be reset if OVERWRITE_LOCAL, so, we should not fix state in this case
        return commitIfCan(indicator, if (!fixStateIfCannotCommit || syncType == SyncType.OVERWRITE_LOCAL) repository.repositoryState else repository.fixAndGetState())
      }
      catch (e: UnmergedPathsException) {
        if (syncType == SyncType.OVERWRITE_LOCAL) {
          LOG.warn("Unmerged detected, ignored because sync type is OVERWRITE_LOCAL", e)
          return false
        }
        else {
          indicator?.checkCanceled()
          LOG.warn("Unmerged detected, will be attempted to resolve", e)
          resolveUnmergedConflicts(repository)
          indicator?.checkCanceled()
          return commitIfCan(indicator, repository.fixAndGetState())
        }
      }
      catch (e: NoHeadException) {
        LOG.warn("Cannot commit - no HEAD", e)
        return false
      }
    }
  }

  private fun commitIfCan(indicator: ProgressIndicator?, state: RepositoryState): Boolean {
    if (state.canCommit()) {
      return commit(repository, indicator)
    }
    else {
      LOG.warn("Cannot commit, repository in state ${state.description}")
      return false
    }
  }

  override fun getAheadCommitsCount() = repository.getAheadCommitsCount()

  override fun commit(paths: List<String>) {
  }

  override fun push(indicator: ProgressIndicator?) {
    LOG.debug("Push")

    val refSpecs = SmartList(RemoteConfig(repository.config, Constants.DEFAULT_REMOTE_NAME).pushRefSpecs)
    if (refSpecs.isEmpty()) {
      val head = repository.findRef(Constants.HEAD)
      if (head != null && head.isSymbolic) {
        refSpecs.add(RefSpec(head.leaf.name))
      }
    }

    val monitor = indicator.asProgressMonitor()
    for (transport in Transport.openAll(repository, Constants.DEFAULT_REMOTE_NAME, Transport.Operation.PUSH)) {
      for (attempt in 0..1) {
        transport.credentialsProvider = credentialsProvider
        try {
          val result = transport.push(monitor, transport.findRemoteRefUpdatesFor(refSpecs))
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

  override fun fetch(indicator: ProgressIndicator?): Updater {
    val pullTask = Pull(this, indicator ?: EmptyProgressIndicator())
    val refToMerge = pullTask.fetch()
    return object : Updater {
      override var definitelySkipPush = false

      // KT-8632
      override fun merge(): UpdateResult? = lock.write {
        val committed = commit(pullTask.indicator)
        if (refToMerge == null) {
          definitelySkipPush = !committed && getAheadCommitsCount() == 0
          return null
        }
        return pullTask.pull(prefetchedRefToMerge = refToMerge)
      }
    }
  }

  override fun pull(indicator: ProgressIndicator?) = Pull(this, indicator).pull()

  override fun resetToTheirs(indicator: ProgressIndicator) = Reset(this, indicator).reset(true)

  override fun resetToMy(indicator: ProgressIndicator, localRepositoryInitializer: (() -> Unit)?) = Reset(this, indicator).reset(false, localRepositoryInitializer)

  override fun canCommit() = repository.repositoryState.canCommit()

  fun renameDirectory(pairs: Map<String, String?>): Boolean {
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
          try {
            if (file.isHidden()) {
              file.delete()
            }
            else {
              Files.move(file, new.resolve(file.fileName))
              if (addCommand == null) {
                addCommand = AddCommand(repository)
              }
              addCommand!!.addFilepattern(if (newPath == null) file.fileName.toString() else "$newPath/${file.fileName}")
            }
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
        toDelete.add(DeleteDirectory(oldPath))
      }

      try {
        old.delete()
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    if (toDelete.isEmpty() && addCommand == null) {
      return false
    }

    repository.edit(toDelete)
    if (addCommand != null) {
      addCommand!!.call()
    }

    repository.commit(with(IdeaCommitMessageFormatter()) { StringBuilder().appendCommitOwnerInfo(true) }.append("Get rid of \$ROOT_CONFIG$ and \$APP_CONFIG").toString())
    return true
  }

  private fun getIgnoreRules(): IgnoreNode? {
    var node = ignoreRules
    if (node == null) {
      val file = dir.resolve(Constants.DOT_GIT_IGNORE)
      if (file.exists()) {
        node = IgnoreNode()
        file.inputStream().use { node!!.parse(it) }
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
      FileRepositoryBuilder().setGitDir(file.toFile()).setMustExist(true).build()
    }
    catch (e: IOException) {
      return false
    }

    return true
  }
}