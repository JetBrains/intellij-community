package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.FetchResult
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.settingsRepository.AuthenticationException
import org.jetbrains.settingsRepository.LOG
import java.io.File
import java.io.InputStream

fun wrapIfNeedAndReThrow(e: TransportException) {
  if (e.getStatus() == TransportException.Status.CANNOT_RESOLVE_REPO) {
    throw org.jetbrains.settingsRepository.NoRemoteRepositoryException(e)
  }

  val message = e.getMessage()!!
  if (e.getStatus() == TransportException.Status.NOT_AUTHORIZED || e.getStatus() == TransportException.Status.NOT_PERMITTED ||
      message.contains(JGitText.get().notAuthorized) || message.contains("Auth cancel") || message.contains("Auth fail") || message.contains(": reject HostKey:") /* JSch */) {
    throw AuthenticationException(e)
  }
  else if (e.getStatus() == TransportException.Status.CANCELLED || message == "Download cancelled") {
    throw ProcessCanceledException()
  }
  else {
    throw e
  }
}

fun Repository.fetch(remoteConfig: RemoteConfig, credentialsProvider: CredentialsProvider? = null, progressMonitor: ProgressMonitor? = null): FetchResult? {
  val transport = Transport.open(this, remoteConfig)
  try {
    transport.setCredentialsProvider(credentialsProvider)
    return transport.fetch(progressMonitor ?: NullProgressMonitor.INSTANCE, null)
  }
  catch (e: TransportException) {
    val message = e.getMessage()!!
    if (message.startsWith("Remote does not have ")) {
      LOG.info(message)
      // "Remote does not have refs/heads/master available for fetch." - remote repository is not initialized
      return null
    }

    wrapIfNeedAndReThrow(e)
    return null
  }
  finally {
    transport.close()
  }
}

fun Repository.disableAutoCrLf(): Repository {
  val config = getConfig()
  config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE)
  config.save()
  return this
}

fun createBareRepository(dir: File): Repository {
  val repository = FileRepositoryBuilder().setBare().setGitDir(dir).build()
  repository.create(true)
  return repository
}

fun createRepository(dir: File): Repository {
  val repository = FileRepositoryBuilder().setWorkTree(dir).build()
  repository.create()
  return repository
}

fun Repository.commit(message: String? = null, reflogComment: String? = null, author: PersonIdent? = null, committer: PersonIdent? = null): RevCommit {
  val commitCommand = CommitCommand(this).setAuthor(author).setCommitter(committer)
  if (message != null) {
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
  return resetCommand.getDirCacheCheckout()!!
}

fun Config.getRemoteBranchFullName(): String {
  val name = getString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE)
  if (StringUtil.isEmpty(name)) {
    throw IllegalStateException("branch.master.merge refspec must be specified")
  }
  return name!!
}

public fun Repository.setUpstream(url: String?, branchName: String = Constants.MASTER): StoredConfig {
  // our local branch named 'master' in any case
  val localBranchName = Constants.MASTER

  val config = getConfig()
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

public fun Repository.computeIndexDiff(): IndexDiff {
  val workingTreeIterator = FileTreeIterator(this)
  try {
    return IndexDiff(this, Constants.HEAD, workingTreeIterator)
  }
  finally {
    workingTreeIterator.reset()
  }
}

public fun cloneBare(uri: String, dir: File, credentialsStore: NotNullLazyValue<CredentialsStore>? = null, progressMonitor: ProgressMonitor = NullProgressMonitor.INSTANCE): Repository {
  val repository = createBareRepository(dir)
  val config = repository.setUpstream(uri)
  val remoteConfig = RemoteConfig(config, Constants.DEFAULT_REMOTE_NAME)

  val result = repository.fetch(remoteConfig, if (credentialsStore == null) null else JGitCredentialsProvider(credentialsStore, repository), progressMonitor) ?: return repository
  var head = findBranchToCheckout(result)
  if (head == null) {
    val branch = Constants.HEAD
    head = result.getAdvertisedRef(branch) ?: result.getAdvertisedRef(Constants.R_HEADS + branch) ?: result.getAdvertisedRef(Constants.R_TAGS + branch)
  }

  if (head == null || head.getObjectId() == null) {
    return repository
  }

  if (head.getName().startsWith(Constants.R_HEADS)) {
    val newHead = repository.updateRef(Constants.HEAD)
    newHead.disableRefLog()
    newHead.link(head.getName())
    val branchName = Repository.shortenRefName(head.getName())
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE, Constants.DEFAULT_REMOTE_NAME)
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, head.getName())
    val autoSetupRebase = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE)
    if (ConfigConstants.CONFIG_KEY_ALWAYS == autoSetupRebase || ConfigConstants.CONFIG_KEY_REMOTE == autoSetupRebase) {
      config.setBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REBASE, true)
    }
    config.save()
  }

  val rewWalk = RevWalk(repository)
  val commit: RevCommit
  try {
    commit = rewWalk.parseCommit(head.getObjectId())
  }
  finally {
    rewWalk.release()
  }

  val u = repository.updateRef(Constants.HEAD, !head.getName().startsWith(Constants.R_HEADS))
  u.setNewObjectId(commit.getId())
  u.forceUpdate()
  return repository
}

private fun findBranchToCheckout(result: FetchResult): Ref? {
  val idHead = result.getAdvertisedRef(Constants.HEAD) ?: return null

  val master = result.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER)
  if (master != null && master.getObjectId().equals(idHead.getObjectId())) {
    return master
  }

  for (r in result.getAdvertisedRefs()) {
    if (!r.getName().startsWith(Constants.R_HEADS)) {
      continue
    }
    if (r.getObjectId().equals(idHead.getObjectId())) {
      return r
    }
  }
  return null
}

public fun Repository.read(path: String): InputStream? {
  val lastCommitId = resolve(Constants.HEAD) ?: return null
  val reader = newObjectReader()
  var releaseReader = true
  try {
    val treeWalk = TreeWalk.forPath(reader, path, RevWalk(reader).parseCommit(lastCommitId).getTree()) ?: return null
    val objectLoader = reader.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB)
    val input = objectLoader.openStream()
    if (objectLoader.isLarge()) {
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
      reader.release()
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
      reader.release();
    }
  }
}

fun ObjectReader.getCachedBytes(dirCacheEntry: DirCacheEntry) = open(dirCacheEntry.getObjectId(), Constants.OBJ_BLOB).getCachedBytes()