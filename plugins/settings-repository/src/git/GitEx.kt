package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.dircache.*
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.FileTreeIterator

import java.io.File
import java.io.IOException
import org.jetbrains.plugins.settingsRepository.LOG
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit
import java.io.FileInputStream

fun Repository.disableAutoCrLf() {
  val config = getConfig()
  config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE)
  config.save()
}

fun createBareRepository(dir: File) {
  FileRepositoryBuilder().setBare().setGitDir(dir).build().create(true)
}

private abstract class PathEditEx(path: String) : PathEdit(path) {
  var repository: Repository? = null
}

class AddLoadedFile(path: String, private val content: ByteArray, private val size: Int, private val lastModified: Long) : PathEditEx(path) {
  override fun apply(entry: DirCacheEntry) {
    entry.setFileMode(FileMode.REGULAR_FILE)
    entry.setLength(size)
    entry.setLastModified(lastModified)

    val inserter = repository!!.newObjectInserter()
    try {
      entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content, 0, size))
      inserter.flush()
    }
    finally {
      inserter.release()
    }
  }
}

class AddFile(private val path: String) : PathEditEx(path) {
  override fun apply(entry: DirCacheEntry) {
    val file = File(repository!!.getWorkTree(), path)
    entry.setFileMode(FileMode.REGULAR_FILE)
    val length = file.length()
    entry.setLength(length)
    entry.setLastModified(file.lastModified())

    val input = FileInputStream(file)
    val inserter = repository!!.newObjectInserter()
    try {
      entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, length, input))
      inserter.flush()
    }
    finally {
      inserter.release()
      input.close()
    }
  }
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

public fun Repository.edit(edit: PathEdit) {
  val dirCache = lockDirCache()
  try {
    val editor = dirCache.editor()
    if (edit is PathEditEx) {
      edit.repository = this
    }
    editor.add(edit)
    editor.commit()
  }
  finally {
    dirCache.unlock()
  }
}

public fun Repository.edit(edits: List<PathEdit>) {
  val dirCache = lockDirCache()
  try {
    val editor = dirCache.editor()
    for (edit in edits) {
      if (edit is PathEditEx) {
        edit.repository = this
      }
      editor.add(edit)
    }
    editor.commit()
  }
  finally {
    dirCache.unlock()
  }
}

public fun Repository.remove(path: String, isFile: Boolean) {
  edit((if (isFile) DirCacheEditor.DeletePath(path) else DirCacheEditor.DeleteTree(path)))
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