package org.jetbrains.settingsRepository.git

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.dircache.*
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.FileTreeIterator

import java.io.File
import java.io.IOException
import org.eclipse.jgit.api.ResetCommand
import org.jetbrains.settingsRepository.LOG
import org.eclipse.jgit.api.CommitCommand

fun Repository.disableAutoCrLf() {
  val config = getConfig()
  config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE)
  config.save()
}

fun createBareRepository(dir: File) {
  FileRepositoryBuilder().setBare().setGitDir(dir).build().create(true)
}

fun Repository.commit() {
  CommitCommand(this).call()
}

fun Repository.resetHard() {
  ResetCommand(this).setMode(ResetCommand.ResetType.HARD).call()
}

fun Config.getRemoteBranchFullName(): String {
  val name = getString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE)
  if (StringUtil.isEmpty(name)) {
    throw IllegalStateException("branch.master.merge refspec must be specified")
  }
  return name!!
}

throws(javaClass<IOException>())
fun Repository.setUpstream(url: String?, branchName: String = Constants.MASTER) {
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
    LOG.debug("Set remote " + url)
    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_KEY_URL, url)
    // http://git-scm.com/book/en/Git-Internals-The-Refspec
    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_FETCH_SECTION, '+' + Constants.R_HEADS + branchName + ':' + Constants.R_REMOTES + remoteName + '/' + branchName)
    // todo should we set it if fetch specified (kirill.likhodedov suggestion)
    //config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, "push", Constants.R_HEADS + localBranchName + ':' + Constants.R_HEADS + branchName);

    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName, ConfigConstants.CONFIG_KEY_REMOTE, remoteName)
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName)
  }
  config.save()
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

fun ObjectReader.getCachedBytes(dirCacheEntry: DirCacheEntry) = open(dirCacheEntry.getObjectId(), Constants.OBJ_BLOB).getCachedBytes()