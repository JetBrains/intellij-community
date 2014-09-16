package git

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.ObjectId
import com.intellij.openapi.vcs.merge.MergeProvider
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.lib.AnyObjectId
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vcs.merge.MergeData
import org.eclipse.jgit.treewalk.TreeWalk
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class JGitMergeProvider(private val repository: Repository, private val myCommit: ObjectId, private val theirsCommit: ObjectId) : MergeProvider {
  private var revWalk: RevWalk? = null

  private fun getData(commitId: AnyObjectId, path: String): ByteArray {
    if (revWalk == null) {
      revWalk = RevWalk(repository)
    }

    try {
      val commit = revWalk!!.parseCommit(commitId)
      val treeWalk = TreeWalk.forPath(repository, path, commit.getTree())
      return repository.open(treeWalk.getObjectId(0)).getCachedBytes()
    }
    finally {
      revWalk!!.release()
    }
  }

  override fun conflictResolvedForFile(file: VirtualFile) {
  }

  override fun isBinary(file: VirtualFile) = file.getFileType().isBinary()

  override fun loadRevisions(file: VirtualFile): MergeData {
    val mergeData = MergeData()
    val filePath = file.getPath()
    mergeData.ORIGINAL = getData(myCommit, filePath)
    mergeData.LAST = getData(theirsCommit, filePath)
    mergeData.CURRENT = FileUtil.loadFileBytes(File(repository.getWorkTree(), filePath))
    return mergeData
  }
}