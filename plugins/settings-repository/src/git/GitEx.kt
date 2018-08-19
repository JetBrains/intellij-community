// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.nullize
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
import org.jetbrains.settingsRepository.AuthenticationException
import org.jetbrains.settingsRepository.IcsCredentialsStore
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
    Transport.open(this, remoteConfig).use { transport ->
      transport.credentialsProvider = credentialsProvider
      transport.isRemoveDeletedRefs = true
      return transport.fetch(progressMonitor ?: NullProgressMonitor.INSTANCE, null)
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

val Repository.upstream: String?
    get() = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL).nullize()

fun Repository.setUpstream(url: String?, branchName: String = Constants.MASTER): StoredConfig {
  // our local branch named 'master' in any case
  val localBranchName = Constants.MASTER

  val config = config
  val remoteName = Constants.DEFAULT_REMOTE_NAME
  if (url.isNullOrEmpty()) {
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

fun cloneBare(uri: String, dir: Path, credentialsStore: Lazy<IcsCredentialsStore>? = null, progressMonitor: ProgressMonitor = NullProgressMonitor.INSTANCE): Repository {
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

  val commit = RevWalk(repository).use { it.parseCommit(head.objectId) }
  val u = repository.updateRef(Constants.HEAD, !head.name.startsWith(Constants.R_HEADS))
  u.setNewObjectId(commit.id)
  u.forceUpdate()
  return repository
}

private fun findBranchToCheckout(result: FetchResult): Ref? {
  val idHead = result.getAdvertisedRef(Constants.HEAD) ?: return null

  val master = result.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER)
  if (master != null && master.objectId == idHead.objectId) {
    return master
  }

  return result.advertisedRefs.firstOrNull { it.name.startsWith(Constants.R_HEADS) && it.objectId == idHead.objectId }
}

fun Repository.processChildren(path: String, filter: ((name: String) -> Boolean)? = null, processor: (name: String, inputStream: InputStream) -> Boolean) {
  val lastCommitId = resolve(Constants.FETCH_HEAD) ?: return
  val reader = newObjectReader()
  reader.use {
    val rootTreeWalk = TreeWalk.forPath(reader, path, RevWalk(reader).parseCommit(lastCommitId).tree) ?: return
    if (!rootTreeWalk.isSubtree) {
      // not a directory
      LOG.warn("File $path is not a directory")
      return
    }

    // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/api/ListFilesOfCommitAndTag.java
    val treeWalk = TreeWalk(this)
    treeWalk.addTree(rootTreeWalk.getObjectId(0))
    treeWalk.isRecursive = false
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

        if (!objectLoader.openStream().use { processor(fileName, it) }) {
          break
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

  override fun equals(other: Any?) = delegate == other

  override fun available() = delegate.available()

  override fun close() {
    try {
      delegate.close()
    }
    finally {
      reader.close()
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

  return walk.count()
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

// FileRepositoryBuilder must be not used directly - using of system config must be disabled (no need, to avoid git exe discovering - it can cause https://youtrack.jetbrains.com/issue/IDEA-170795)
fun buildRepository(workTree: Path? = null, bare: Boolean = false, gitDir: Path? = null, mustExists: Boolean = false): Repository {
  return with(FileRepositoryBuilder().setUseSystemConfig(false)) {
    if (bare) {
      setBare()
    }
    else {
      workTree?.let {
        setWorkTree(it.toFile())
      }
    }
    gitDir?.let {
      setGitDir(gitDir.toFile())
    }

    isMustExist = mustExists

    build()
  }
}

fun buildBareRepository(gitDir: Path): Repository = buildRepository(bare = true, gitDir = gitDir)

fun createBareRepository(dir: Path): Repository {
  val repository = buildRepository(bare = true, gitDir = dir)
  repository.create(true)
  return repository
}