package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.settingsRepository.BaseRepositoryManager
import org.jetbrains.plugins.settingsRepository.CredentialsStore

import java.io.File
import java.io.IOException
import org.jetbrains.plugins.settingsRepository.LOG
import org.eclipse.jgit.api.Git

public class GitRepositoryManager(private val credentialsStore: NotNullLazyValue<CredentialsStore>) : BaseRepositoryManager() {
  val git: Git
  val repository: Repository

  private var _credentialsProvider: CredentialsProvider? = null

  val credentialsProvider: CredentialsProvider
    get() {
      if (_credentialsProvider == null) {
        _credentialsProvider = JGitCredentialsProvider(credentialsStore, repository)
      }
      return _credentialsProvider!!
    }

  {
    repository = FileRepositoryBuilder().setWorkTree(dir).build()
    git = Git(repository)
    if (!dir.exists()) {
      repository.create()
      repository.disableAutoCrLf()
    }
  }

  override fun initRepository(dir: File) {
    createBareRepository(dir)
  }

  override fun getRemoteRepositoryUrl(): String? {
    return StringUtil.nullize(repository.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL))
  }

  override fun setUpstream(url: String?, branch: String?) {
    repository.setUpstream(url, branch ?: Constants.MASTER)
  }

  fun createCommitCommand(): CommitCommand {
    val author = PersonIdent(repository)
    val committer = PersonIdent(ApplicationInfoEx.getInstanceEx()!!.getFullApplicationName(), author.getEmailAddress())
    return git.commit().setAuthor(author).setCommitter(committer)
  }

  override fun hasUpstream(): Boolean {
    return !StringUtil.isEmptyOrSpaces(repository.getConfig().getString("remote", "origin", "url"))
  }

  override fun addToIndex(file: File, path: String, content: ByteArray, size: Int) {
    repository.edit(AddLoadedFile(path, content, size, file.lastModified()))
  }

  override fun deleteFromIndex(path: String, isFile: Boolean) {
    repository.remove(path, isFile)
  }

  override fun commit(indicator: ProgressIndicator) {
    synchronized (lock) {
      commit(this, indicator)
    }
  }

  override fun commit(paths: List<String>) {
  }

  override fun push(indicator: ProgressIndicator) {
    LOG.debug("Push")

    val refSpecs = SmartList(RemoteConfig(repository.getConfig(), Constants.DEFAULT_REMOTE_NAME).getPushRefSpecs())
    if (refSpecs.isEmpty()) {
      val head = repository.getRef(Constants.HEAD)
      if (head != null && head.isSymbolic())
        refSpecs.add(RefSpec(head.getLeaf().getName()))
    }

    val monitor = JGitProgressMonitor(indicator)
    for (transport in Transport.openAll(repository, Constants.DEFAULT_REMOTE_NAME, Transport.Operation.PUSH)) {
      transport.setCredentialsProvider(credentialsProvider)

      try {
        val result = transport.push(monitor, transport.findRemoteRefUpdatesFor(refSpecs))
        if (LOG.isDebugEnabled()) {
          printMessages(result)

          for (refUpdate in result.getRemoteUpdates()) {
            LOG.debug(refUpdate.toString())
          }
        }
      }
      catch (e: TransportException) {
        wrapIfNeedAndReThrow(e)
      }
      finally {
        transport.close()
      }
    }
  }

  override fun pull(indicator: ProgressIndicator) {
    pull(this, indicator)
  }

  override fun isValidRepository(file: File): Boolean {
    if (File(file, Constants.DOT_GIT).exists()) {
      return true
    }
    // existing bare repository
    try {
      FileRepositoryBuilder().setGitDir(file).setMustExist(true).build()
    }
    catch (e: IOException) {
      return false
    }

    return true
  }
}

fun printMessages(fetchResult: OperationResult) {
  if (LOG.isDebugEnabled()) {
    val messages = fetchResult.getMessages()
    if (!StringUtil.isEmptyOrSpaces(messages)) {
      LOG.debug(messages)
    }
  }
}
