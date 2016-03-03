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

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.FetchResult
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.settingsRepository.AuthenticationException
import org.jetbrains.settingsRepository.LOG
import java.io.InputStream
import java.nio.file.Path

fun wrapIfNeedAndReThrow(e: TransportException) {
  if (e is org.eclipse.jgit.errors.NoRemoteRepositoryException || e.status == TransportException.Status.CANNOT_RESOLVE_REPO) {
    throw org.jetbrains.settingsRepository.NoRemoteRepositoryException(e)
  }

  val message = e.message!!
  if (e.status == TransportException.Status.NOT_AUTHORIZED || e.status == TransportException.Status.NOT_PERMITTED ||
      message.contains(JGitText.get().notAuthorized) || message.contains("Auth cancel") || message.contains("Auth fail") || message.contains(": reject HostKey:") /* JSch */) {
    throw AuthenticationException(e)
  }
  else if (e.status == TransportException.Status.CANCELLED || message == "Download cancelled") {
    throw ProcessCanceledException()
  }
  else {
    throw e
  }
}

fun Repository.fetch(remoteConfig: RemoteConfig, credentialsProvider: CredentialsProvider? = null, progressMonitor: ProgressMonitor? = null): FetchResult? {
  try {
    val transport = Transport.open(this, remoteConfig)
    try {
      transport.credentialsProvider = credentialsProvider
      return transport.fetch(progressMonitor ?: NullProgressMonitor.INSTANCE, null)
    }
    finally {
      transport.close()
    }
  }
  catch (e: TransportException) {
    val message = e.message!!
    if (message.startsWith("Remote does not have ")) {
      LOG.info(message)
      // "Remote does not have refs/heads/master available for fetch." - remote repository is not initialized
      return null
    }

    wrapIfNeedAndReThrow(e)
    return null
  }
}

fun Repository.disableAutoCrLf(): Repository {
  val config = config
  config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE)
  config.save()
  return this
}

fun createBareRepository(dir: Path): Repository {
  val repository = FileRepositoryBuilder().setBare().setGitDir(dir.toFile()).build()
  repository.create(true)
  return repository
}

fun createRepository(dir: Path): Repository {
  val repository = FileRepositoryBuilder().setWorkTree(dir.toFile()).build()
  repository.create()
  return repository
}

fun Repository.commit(message: String? = null, reflogComment: String? = null, author: PersonIdent? = null, committer: PersonIdent? = null): RevCommit {
  val commitCommand = CommitCommand(this).setAuthor(author).setCommitter(committer)
  if (message != null) {
    @Suppress("UsePropertyAccessSyntax")
    commitCommand.setMessage(message)
  }
  if (reflogComment != null) {
    commitCommand.setReflogComment(reflogComment)
  }
  return commitCommand.call()
}

fun Repository.resetHard(): DirCacheCheckout {
  val resetCommand = ResetCommand(this).setMode(ResetCommand.ResetType.HARD)
  resetCommand.call()
  return resetCommand.dirCacheCheckout!!
}

fun Config.getRemoteBranchFullName(): String {
  val name = getString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE)
  if (StringUtil.isEmpty(name)) {
    throw IllegalStateException("branch.master.merge refspec must be specified")
  }
  return name!!
}

fun Repository.setUpstream(url: String?, branchName: String = Constants.MASTER): StoredConfig {
  // our local branch named 'master' in any case
  val localBranchName = Constants.MASTER

  val config = config
  val remoteName = Constants.DEFAULT_REMOTE_NAME
  if (StringUtil.isEmptyOrSpaces(url)) {
    LOG.debug("Unset remote")
    config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName)
    config.unsetSection(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName)
  }
  else {
    LOG.debug("Set remote $url")
    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_KEY_URL, url)
    // http://git-scm.com/book/en/Git-Internals-The-Refspec
    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_FETCH_SECTION, '+' + Constants.R_HEADS + branchName + ':' + Constants.R_REMOTES + remoteName + '/' + branchName)
    // todo should we set it if fetch specified (kirill.likhodedov suggestion)
    //config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, "push", Constants.R_HEADS + localBranchName + ':' + Constants.R_HEADS + branchName);

    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName, ConfigConstants.CONFIG_KEY_REMOTE, remoteName)
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName)
  }
  config.save()
  return config
}

fun Repository.computeIndexDiff(): IndexDiff {
  val workingTreeIterator = FileTreeIterator(this)
  try {
    return IndexDiff(this, Constants.HEAD, workingTreeIterator)
  }
  finally {
    workingTreeIterator.reset()
  }
}

fun cloneBare(uri: String, dir: Path, credentialsStore: NotNullLazyValue<CredentialsStore>? = null, progressMonitor: ProgressMonitor = NullProgressMonitor.INSTANCE): Repository {
  val repository = createBareRepository(dir)
  val config = repository.setUpstream(uri)
  val remoteConfig = RemoteConfig(config, Constants.DEFAULT_REMOTE_NAME)

  val result = repository.fetch(remoteConfig, if (credentialsStore == null) null else JGitCredentialsProvider(credentialsStore, repository), progressMonitor) ?: return repository
  var head = findBranchToCheckout(result)
  if (head == null) {
    val branch = Constants.HEAD
    head = result.getAdvertisedRef(branch) ?: result.getAdvertisedRef(Constants.R_HEADS + branch) ?: result.getAdvertisedRef(Constants.R_TAGS + branch)
  }

  if (head == null || head.objectId == null) {
    return repository
  }

  if (head.name.startsWith(Constants.R_HEADS)) {
    val newHead = repository.updateRef(Constants.HEAD)
    newHead.disableRefLog()
    newHead.link(head.name)
    val branchName = Repository.shortenRefName(head.name)
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE, Constants.DEFAULT_REMOTE_NAME)
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, head.name)
    val autoSetupRebase = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE)
    if (ConfigConstants.CONFIG_KEY_ALWAYS == autoSetupRebase || ConfigConstants.CONFIG_KEY_REMOTE == autoSetupRebase) {
      config.setBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REBASE, true)
    }
    config.save()
  }

  val commit = RevWalk(repository).use { it.parseCommit(head!!.objectId) }
  val u = repository.updateRef(Constants.HEAD, !head.name.startsWith(Constants.R_HEADS))
  u.setNewObjectId(commit.id)
  u.forceUpdate()
  return repository
}

private fun findBranchToCheckout(result: FetchResult): Ref? {
  val idHead = result.getAdvertisedRef(Constants.HEAD) ?: return null

  val master = result.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER)
  if (master != null && master.objectId.equals(idHead.objectId)) {
    return master
  }

  for (r in result.advertisedRefs) {
    if (!r.name.startsWith(Constants.R_HEADS)) {
      continue
    }
    if (r.objectId.equals(idHead.objectId)) {
      return r
    }
  }
  return null
}

fun Repository.processChildren(path: String, filter: ((name: String) -> Boolean)? = null, processor: (name: String, inputStream: InputStream) -> Boolean) {
  val lastCommitId = resolve(Constants.HEAD) ?: return
  val reader = newObjectReader()
  reader.use {
    val treeWalk = TreeWalk.forPath(reader, path, RevWalk(reader).parseCommit(lastCommitId).tree) ?: return
    if (!treeWalk.isSubtree) {
      // not a directory
      LOG.warn("File $path is not a directory")
      return
    }

    treeWalk.filter = TreeFilter.ALL
    treeWalk.enterSubtree()

    while (treeWalk.next()) {
      val fileMode = treeWalk.getFileMode(0)
      if (fileMode == FileMode.REGULAR_FILE || fileMode == FileMode.SYMLINK || fileMode == FileMode.EXECUTABLE_FILE) {
        val fileName = treeWalk.nameString
        if (filter != null && !filter(fileName)) {
          continue
        }

        val objectLoader = reader.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB)
        // we ignore empty files
        if (objectLoader.size == 0L) {
          LOG.warn("File $path skipped because empty (length 0)")
          continue
        }

        if (!processor(fileName, objectLoader.openStream())) {
          break;
        }
      }
    }
  }
}

fun Repository.read(path: String): InputStream? {
  val lastCommitId = resolve(Constants.HEAD)
  if (lastCommitId == null) {
    LOG.warn("Repository ${directory.name} doesn't have HEAD")
    return null
  }

  val reader = newObjectReader()
  var releaseReader = true
  try {
    val treeWalk = TreeWalk.forPath(reader, path, RevWalk(reader).parseCommit(lastCommitId).tree) ?: return null
    val objectLoader = reader.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB)
    val input = objectLoader.openStream()
    if (objectLoader.isLarge) {
      // we cannot release reader because input uses it internally (window cursor -> inflater)
      releaseReader = false
      return InputStreamWrapper(input, reader)
    }
    else {
      return input
    }
  }
  finally {
    if (releaseReader) {
      reader.close()
    }
  }
}

private class InputStreamWrapper(private val delegate: InputStream, private val reader: ObjectReader) : InputStream() {
  override fun read() = delegate.read()

  override fun read(b: ByteArray) = delegate.read(b)

  override fun read(b: ByteArray, off: Int, len: Int) = delegate.read(b, off, len)

  override fun hashCode() = delegate.hashCode()

  override fun toString() = delegate.toString()

  override fun reset() = delegate.reset()

  override fun mark(limit: Int) = delegate.mark(limit)

  override fun skip(n: Long): Long {
    return super.skip(n)
  }

  override fun markSupported() = delegate.markSupported()

  override fun equals(other: Any?) = delegate.equals(other)

  override fun available() = delegate.available()

  override fun close() {
    try {
      delegate.close()
    }
    finally {
      reader.close();
    }
  }
}

fun Repository.getAheadCommitsCount(): Int {
  val config = config
  val shortBranchName = Repository.shortenRefName(config.getRemoteBranchFullName())
  val trackingBranch = BranchConfig(config, shortBranchName).trackingBranch ?: return -1
  val local = exactRef("${Constants.R_HEADS}$shortBranchName") ?: return -1
  val walk = RevWalk(this)
  val localCommit = walk.parseCommit(local.objectId)

  val trackingCommit = findRef(trackingBranch)?.let { walk.parseCommit(it.objectId) }

  walk.revFilter = RevFilter.MERGE_BASE
  if (trackingCommit == null) {
    walk.markStart(localCommit)
    walk.sort(RevSort.REVERSE)
  }
  else {
    walk.markStart(localCommit)
    walk.markStart(trackingCommit)
    val mergeBase = walk.next()
    walk.reset()

    walk.markStart(localCommit)
    walk.markUninteresting(mergeBase)
  }

  walk.revFilter = RevFilter.ALL

  var num = 0
  for (c in walk) {
    num++
  }
  return num
}

inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
  var closed = false
  try {
    return block(this)
  }
  catch (e: Exception) {
    closed = true
    try {
      close()
    }
    catch (closeException: Exception) {
      @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
      (e as java.lang.Throwable).addSuppressed(closeException)
    }
    throw e
  }
  finally {
    if (!closed) {
      close()
    }
  }
}
