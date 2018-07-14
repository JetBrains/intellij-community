// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.ArrayUtil.newLongArray
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.svn.SvnPropertyKeys.MERGE_INFO
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.parseUrl
import org.jetbrains.idea.svn.api.Depth
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import org.jetbrains.idea.svn.dialogs.WCInfo
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches
import org.jetbrains.idea.svn.history.SvnChangeList
import org.jetbrains.idea.svn.history.SvnRepositoryLocation
import org.jetbrains.idea.svn.integrate.MergeContext
import org.jetbrains.idea.svn.mergeinfo.BranchInfo
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

private const val CONTENT1 = "123\n456\n123"
private const val CONTENT2 = "123\n456\n123\n4"

private fun assertRevisions(changeLists: List<SvnChangeList>, vararg expectedTopRevisions: Long) {
  val revisions = newLongArray(expectedTopRevisions.size)
  for (i in revisions.indices) {
    revisions[i] = changeLists[i].number
  }

  assertArrayEquals(expectedTopRevisions, revisions)
}

private fun newFile(parent: File, name: String): File {
  val f1 = File(parent, name)
  f1.createNewFile()
  return f1
}

private fun newFolder(parent: String, name: String): File {
  val trunk = File(parent, name)
  trunk.mkdir()
  return trunk
}

private fun newFolder(parent: File, name: String): File {
  val trunk = File(parent, name)
  trunk.mkdir()
  return trunk
}

class SvnMergeInfoTest : SvnTestCase() {
  private lateinit var myBranchVcsRoot: File
  private lateinit var myOneShotMergeInfoHelper: OneShotMergeInfoHelper
  private lateinit var myMergeChecker: BranchInfo

  private lateinit var trunk: File
  private lateinit var folder: File
  private lateinit var f1: File
  private lateinit var f2: File

  private lateinit var myTrunkUrl: String
  private lateinit var myBranchUrl: String

  private val trunkChangeLists: List<SvnChangeList>
    @Throws(com.intellij.openapi.vcs.VcsException::class)
    get() {
      val provider = vcs.committedChangesProvider

      return provider.getCommittedChanges(provider.createDefaultSettings(), SvnRepositoryLocation(parseUrl(myTrunkUrl, false)), 0)
    }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myTrunkUrl = "$myRepoUrl/trunk"
    myBranchUrl = "$myRepoUrl/branch"

    myBranchVcsRoot = File(myTempDirFixture.tempDirPath, "branch")
    myBranchVcsRoot.mkdir()

    vcsManager.setDirectoryMapping(myBranchVcsRoot.absolutePath, SvnVcs.VCS_NAME)

    val vcsRoot = LocalFileSystem.getInstance().findFileByIoFile(myBranchVcsRoot)
    val node = Node(vcsRoot!!, createUrl(myBranchUrl), createUrl(myRepoUrl))
    val root = RootUrlInfo(node, WorkingCopyFormat.ONE_DOT_SIX, vcsRoot, null)
    val wcInfo = WCInfo(root, true, Depth.INFINITY)
    val mergeContext = MergeContext(vcs, parseUrl(myTrunkUrl, false), wcInfo, Url.tail(myTrunkUrl), vcsRoot)
    myOneShotMergeInfoHelper = OneShotMergeInfoHelper(mergeContext)

    vcs.svnConfiguration.isCheckNestedForQuickMerge = true

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val repoUrl = createUrl(myRepoUrl, false)
    val wcInfoWithBranches = WCInfoWithBranches(wcInfo, emptyList<WCInfoWithBranches.Branch>(), vcsRoot,
                                                WCInfoWithBranches.Branch(repoUrl.appendPath("trunk", false)))
    myMergeChecker = BranchInfo(vcs, wcInfoWithBranches, WCInfoWithBranches.Branch(repoUrl.appendPath("branch", false)))
  }

  @Test
  @Throws(Exception::class)
  fun testSimpleNotMerged() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    assertMergeResult(trunkChangeLists, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testSimpleMerged() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    // rev 4: record as merged into branch
    recordMerge(myBranchVcsRoot, myTrunkUrl, "-c", "3")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3")
    assertMergeResult(trunkChangeLists, SvnMergeInfoCache.MergeCheckResult.MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testEmptyMergeinfoBlocks() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    // rev 4: record as merged into branch
    merge(myBranchVcsRoot, myTrunkUrl, "-c", "3")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)
    // rev5: put blocking empty mergeinfo
    //runInAndVerifyIgnoreOutput("merge", "-c", "-3", myRepoUrl + "/trunk/folder", new File(myBranchVcsRoot, "folder").getAbsolutePath(), "--record-only"));
    merge(File(myBranchVcsRoot, "folder"), "$myTrunkUrl/folder", "-r", "3:2")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3")
    assertMergeInfo(File(myBranchVcsRoot, "folder"), "")

    assertMergeResult(trunkChangeLists, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testNonInheritableMergeinfo() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3*")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3*")

    assertMergeResult(trunkChangeLists, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testOnlyImmediateInheritableMergeinfo() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1, CONTENT1)
    // rev4
    editAndCommit(trunk, f1, CONTENT2)

    updateFile(myBranchVcsRoot)

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3,4")
    setMergeInfo(File(myBranchVcsRoot, "folder"), "/trunk:3")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3-4")
    assertMergeInfo(File(myBranchVcsRoot, "folder"), "/trunk:3")

    val changeListList = trunkChangeLists

    assertRevisions(changeListList, 4, 3)
    assertMergeResult(changeListList, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, SvnMergeInfoCache.MergeCheckResult.MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testTwoPaths() {
    createTwoFolderStructure(myBranchVcsRoot)

    // rev 3
    editFile(f1)
    editFile(f2)
    commitFile(trunk)

    updateFile(myBranchVcsRoot)

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3")
    // this makes not merged for f2 path
    setMergeInfo(File(myBranchVcsRoot, "folder/folder1"), "/trunk:3*")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3")
    assertMergeInfo(File(myBranchVcsRoot, "folder/folder1"), "/trunk:3*")

    val changeListList = trunkChangeLists

    assertRevisions(changeListList, 3)
    assertMergeResult(changeListList, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testWhenInfoInRepo() {
    val fullBranch = newFolder(myTempDirFixture.tempDirPath, "fullBranch")

    createTwoFolderStructure(fullBranch)
    // folder1 will be taken as branch wc root
    checkOutFile("$myBranchUrl/folder/folder1", myBranchVcsRoot)

    // rev 3 : f2 changed
    editAndCommit(trunk, f2)

    // rev 4: record as merged into branch using full branch WC
    recordMerge(fullBranch, myTrunkUrl, "-c", "3")
    commitFile(fullBranch)
    updateFile(myBranchVcsRoot)

    val changeListList = trunkChangeLists

    assertRevisions(changeListList, 3)
    assertMergeResult(changeListList[0], SvnMergeInfoCache.MergeCheckResult.MERGED)
  }

  @Test
  @Throws(Exception::class)
  fun testMixedWorkingRevisions() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3")
    commitFile(myBranchVcsRoot)
    // ! no update!

    assertMergeInfo(myBranchVcsRoot, "/trunk:3")

    val f1info = vcs.getInfo(File(myBranchVcsRoot, "folder/f1.txt"))
    assertEquals(2, f1info!!.revision.number)

    val changeListList = trunkChangeLists
    val changeList = changeListList[0]

    assertMergeResult(changeList, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED)

    // and after update
    updateFile(myBranchVcsRoot)
    myMergeChecker.clear()

    assertMergeResult(changeList, SvnMergeInfoCache.MergeCheckResult.MERGED)
  }

  @Throws(IOException::class)
  private fun createOneFolderStructure() {
    trunk = newFolder(myTempDirFixture.tempDirPath, "trunk")
    folder = newFolder(trunk, "folder")
    f1 = newFile(folder, "f1.txt")
    f2 = newFile(folder, "f2.txt")

    importAndCheckOut(trunk)
  }

  @Throws(IOException::class)
  private fun createTwoFolderStructure(branchFolder: File) {
    trunk = newFolder(myTempDirFixture.tempDirPath, "trunk")
    folder = newFolder(trunk, "folder")
    f1 = newFile(folder, "f1.txt")
    val folder1 = newFolder(folder, "folder1")
    f2 = newFile(folder1, "f2.txt")

    importAndCheckOut(trunk, branchFolder)
  }

  @Throws(IOException::class)
  private fun importAndCheckOut(trunk: File, branch: File = myBranchVcsRoot) {
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.absolutePath, myTrunkUrl)
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myTrunkUrl, myBranchUrl)

    FileUtil.delete(trunk)
    checkOutFile(myTrunkUrl, trunk)
    checkOutFile(myBranchUrl, branch)
  }

  @Throws(IOException::class)
  private fun editAndCommit(trunk: File, file: File, content: String = CONTENT1) {
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

    editAndCommit(trunk, vf!!, content)
  }

  @Throws(IOException::class)
  private fun editAndCommit(trunk: File, file: VirtualFile, content: String) {
    editFile(file, content)
    commitFile(trunk)
  }

  private fun editFile(file: File) {
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

    editFile(vf!!, CONTENT1)
  }

  private fun editFile(file: VirtualFile, content: String) {
    VcsTestUtil.editFileInCommand(myProject, file, content)
  }

  @Throws(SvnBindException::class)
  private fun assertMergeInfo(file: File, expectedValue: String) {
    val propertyValue = vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), MERGE_INFO, false, Revision.WORKING)

    assertNotNull(propertyValue)
    assertEquals(expectedValue, propertyValue!!.toString())
  }

  @Throws(VcsException::class)
  private fun assertMergeResult(changeLists: List<SvnChangeList>, vararg mergeResults: SvnMergeInfoCache.MergeCheckResult) {
    myOneShotMergeInfoHelper.prepare()

    for (i in mergeResults.indices) {
      val changeList = changeLists[i]

      assertMergeResult(changeList, mergeResults[i])
      assertMergeResultOneShot(changeList, mergeResults[i])
    }
  }

  private fun assertMergeResult(changeList: SvnChangeList, mergeResult: SvnMergeInfoCache.MergeCheckResult) {
    assertEquals(mergeResult, myMergeChecker.checkList(changeList, myBranchVcsRoot.absolutePath))
  }

  private fun assertMergeResultOneShot(changeList: SvnChangeList, mergeResult: SvnMergeInfoCache.MergeCheckResult) {
    assertEquals(mergeResult, myOneShotMergeInfoHelper.checkList(changeList))
  }

  @Throws(IOException::class)
  private fun commitFile(file: File) {
    runInAndVerifyIgnoreOutput("ci", "-m", "test", file.absolutePath)
  }

  @Throws(IOException::class)
  private fun updateFile(file: File) {
    runInAndVerifyIgnoreOutput("up", file.absolutePath)
  }

  @Throws(IOException::class)
  private fun checkOutFile(url: String, directory: File) {
    runInAndVerifyIgnoreOutput("co", url, directory.absolutePath)
  }

  @Throws(IOException::class)
  private fun setMergeInfo(file: File, value: String) {
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", value, file.absolutePath)
  }

  @Throws(IOException::class)
  private fun merge(file: File, url: String, vararg revisions: String) {
    merge(file, url, false, *revisions)
  }

  @Throws(IOException::class)
  private fun recordMerge(file: File, url: String, vararg revisions: String) {
    merge(file, url, true, *revisions)
  }

  @Throws(IOException::class)
  private fun merge(file: File, url: String, recordOnly: Boolean, vararg revisions: String) {
    val parameters = ContainerUtil.newArrayList<String>()

    parameters.add("merge")
    ContainerUtil.addAll<String, String, List<String>>(parameters, *revisions)
    parameters.add(url)
    parameters.add(file.absolutePath)
    if (recordOnly) {
      parameters.add("--record-only")
    }

    runInAndVerifyIgnoreOutput(*ArrayUtil.toObjectArray(parameters, String::class.java))
  }
}
