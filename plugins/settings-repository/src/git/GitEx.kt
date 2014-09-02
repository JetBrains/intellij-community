package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.*
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.WorkingTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter

import java.io.File
import java.io.IOException
import org.jetbrains.plugins.settingsRepository.LOG

fun Repository.disableAutoCrLf(): Unit {
  val config = getConfig()
  config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE)
  config.save()
}

public class GitEx(repo: Repository) : Git(repo) {
  throws(javaClass<IOException>())
  public fun setUpstream(url: String?, branchName: String = Constants.MASTER) {
    // our local branch named 'master' in any case
    val localBranchName = Constants.MASTER

    val config = getRepository().getConfig()
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

  public fun add(path: String): Boolean {
    val repository = getRepository()
    val treeWalk = TreeWalk(repository)
    treeWalk.setRecursive(false)

    try {
      return doAdd(treeWalk, path, repository, FileTreeIterator(repository))
    }
    finally {
      treeWalk.release()
    }
  }

  public fun remove(path: String, isFile: Boolean) {
    val repository = getRepository()
    val dirCache = repository.lockDirCache()
    try {
      val editor = dirCache.editor()
      editor.add(if (isFile) DirCacheEditor.DeletePath(path) else DirCacheEditor.DeleteTree(path))
      editor.commit()
    }
    finally {
      dirCache.unlock()
    }
  }

  public fun computeIndexDiff(): IndexDiff {
    val workingTreeIterator = FileTreeIterator(getRepository())
    try {
      return IndexDiff(getRepository(), Constants.HEAD, workingTreeIterator)
    }
    finally {
      workingTreeIterator.reset()
    }
  }
}

public fun createBareRepository(dir: File) {
  FileRepositoryBuilder().setBare().setGitDir(dir).build().create(true)
}

private fun doAdd(treeWalk: TreeWalk, path: String, repository: Repository, workingTreeIterator: WorkingTreeIterator): Boolean {
  val pathFilter = PathFilter.create(path)
  treeWalk.setFilter(pathFilter)

  val dirCache = repository.lockDirCache()
  try {
    val builder = dirCache.builder()
    treeWalk.addTree(DirCacheBuildIterator(builder))
    treeWalk.addTree(workingTreeIterator)

    while (treeWalk.next()) {
      if (pathFilter.isDone(treeWalk)) {
        break
      }
      else if (treeWalk.isSubtree()) {
        treeWalk.enterSubtree()
      }
    }

    return doAdd(treeWalk, path, repository, builder)
  }
  finally {
    dirCache.unlock()
    workingTreeIterator.reset()
  }
}

private fun doAdd(treeWalk: TreeWalk, path: String, repository: Repository, builder: DirCacheBuilder): Boolean {
  val workingTree = treeWalk.getTree<WorkingTreeIterator>(1, javaClass<WorkingTreeIterator>())
  val dirCacheTree = treeWalk.getTree<DirCacheIterator>(0, javaClass<DirCacheIterator>())
  if (dirCacheTree == null && workingTree != null && workingTree.isEntryIgnored()) {
    // file is not in index but is ignored, do nothing
    return true
  }

  if (workingTree != null) {
    // the file exists
    if (dirCacheTree == null || dirCacheTree.getDirCacheEntry() == null || !dirCacheTree.getDirCacheEntry()!!.isAssumeValid()) {
      val mode = workingTree.getIndexFileMode(dirCacheTree)
      val entry = DirCacheEntry(path)
      entry.setFileMode(mode)
      if (mode == FileMode.GITLINK) {
        entry.setObjectId(workingTree.getEntryObjectId())
      }
      else {
        entry.setLength(workingTree.getEntryLength())
        entry.setLastModified(workingTree.getEntryLastModified())
        var inserter: ObjectInserter? = null
        val `in` = workingTree.openEntryStream()
        try {
          inserter = repository.newObjectInserter()
          entry.setObjectId(inserter!!.insert(Constants.OBJ_BLOB, workingTree.getEntryContentLength(), `in`))
          inserter!!.flush()
        }
        finally {
          `in`.close()
          if (inserter != null) {
            inserter!!.release()
          }
        }
      }
      builder.add(entry)
    }
    else {
      builder.add(dirCacheTree.getDirCacheEntry())
    }
  }
  else if (dirCacheTree != null) {
    builder.add(dirCacheTree.getDirCacheEntry())
  }

  builder.commit()
  return false
}