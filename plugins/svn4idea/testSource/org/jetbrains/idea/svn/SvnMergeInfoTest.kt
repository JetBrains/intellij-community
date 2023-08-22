// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.svn.SvnPropertyKeys.MERGE_INFO
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnUtil.parseUrl
import org.jetbrains.idea.svn.api.Depth
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.dialogs.WCInfo
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches
import org.jetbrains.idea.svn.history.SvnChangeList
import org.jetbrains.idea.svn.history.SvnRepositoryLocation
import org.jetbrains.idea.svn.integrate.MergeContext
import org.jetbrains.idea.svn.mergeinfo.BranchInfo
import org.jetbrains.idea.svn.mergeinfo.MergeCheckResult
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private const val CONTENT1 = "123\n456\n123"
private const val CONTENT2 = "123\n456\n123\n4"

private fun assertRevisions(changeLists: List<SvnChangeList>, vararg expectedTopRevisions: Long) =
  assertArrayEquals(expectedTopRevisions, changeLists.take(expectedTopRevisions.size).map { it.number }.toLongArray())

private fun newFile(parent: File, name: String) = File(parent, name).also { it.createNewFile() }
private fun newFolder(parent: String, name: String) = File(parent, name).also { it.mkdir() }
private fun newFolder(parent: File, name: String) = File(parent, name).also { it.mkdir() }

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
    get() {
      val provider = vcs.committedChangesProvider
      return provider.getCommittedChanges(provider.createDefaultSettings(), SvnRepositoryLocation(parseUrl(myTrunkUrl, false)), 0)
    }

  override fun before() {
    super.before()

    myTrunkUrl = "$myRepoUrl/trunk"
    myBranchUrl = "$myRepoUrl/branch"

    myBranchVcsRoot = File(myTempDirFixture.tempDirPath, "branch")
    myBranchVcsRoot.mkdir()

    vcsManager.setDirectoryMapping(myBranchVcsRoot.absolutePath, SvnVcs.VCS_NAME)

    val vcsRoot = LocalFileSystem.getInstance().findFileByIoFile(myBranchVcsRoot)
    val node = Node(vcsRoot!!, createUrl(myBranchUrl), myRepositoryUrl)
    val root = RootUrlInfo(node, WorkingCopyFormat.ONE_DOT_EIGHT, vcsRoot, null)
    val wcInfo = WCInfo(root, true, Depth.INFINITY)
    val mergeContext = MergeContext(vcs, parseUrl(myTrunkUrl, false), wcInfo, Url.tail(myTrunkUrl), vcsRoot)
    myOneShotMergeInfoHelper = OneShotMergeInfoHelper(mergeContext)

    vcs.svnConfiguration.isCheckNestedForQuickMerge = true

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val wcInfoWithBranches = WCInfoWithBranches(wcInfo, emptyList<WCInfoWithBranches.Branch>(), vcsRoot,
                                                WCInfoWithBranches.Branch(myRepositoryUrl.appendPath("trunk", false)))
    myMergeChecker = BranchInfo(vcs, wcInfoWithBranches, WCInfoWithBranches.Branch(myRepositoryUrl.appendPath("branch", false)))
  }

  @Test
  fun testSimpleNotMerged() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    assertMergeResult(trunkChangeLists, MergeCheckResult.NOT_MERGED)
  }

  @Test
  fun testSimpleMerged() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    // rev 4: record as merged into branch
    recordMerge(myBranchVcsRoot, myTrunkUrl, "-c", "3")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3")
    assertMergeResult(trunkChangeLists, MergeCheckResult.MERGED)
  }

  @Test
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
    assertMergeResult(trunkChangeLists, MergeCheckResult.NOT_MERGED)
  }

  @Test
  fun testNonInheritableMergeinfo() {
    createOneFolderStructure()

    // rev 3
    editAndCommit(trunk, f1)

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3*")
    commitFile(myBranchVcsRoot)
    updateFile(myBranchVcsRoot)

    assertMergeInfo(myBranchVcsRoot, "/trunk:3*")
    assertMergeResult(trunkChangeLists, MergeCheckResult.NOT_MERGED)
  }

  @Test
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

    val changeLists = trunkChangeLists
    assertRevisions(changeLists, 4, 3)
    assertMergeResult(changeLists, MergeCheckResult.NOT_MERGED, MergeCheckResult.MERGED)
  }

  @Test
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
    assertMergeResult(changeListList, MergeCheckResult.NOT_MERGED)
  }

  @Test
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
    assertMergeResult(changeListList[0], MergeCheckResult.MERGED)
  }

  @Test
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

    val changeList = trunkChangeLists[0]
    assertMergeResult(changeList, MergeCheckResult.NOT_MERGED)

    // and after update
    updateFile(myBranchVcsRoot)
    myMergeChecker.clear()

    assertMergeResult(changeList, MergeCheckResult.MERGED)
  }

  private fun createOneFolderStructure() {
    trunk = newFolder(myTempDirFixture.tempDirPath, "trunk")
    folder = newFolder(trunk, "folder")
    f1 = newFile(folder, "f1.txt")
    f2 = newFile(folder, "f2.txt")

    importAndCheckOut(trunk)
  }

  private fun createTwoFolderStructure(branchFolder: File) {
    trunk = newFolder(myTempDirFixture.tempDirPath, "trunk")
    folder = newFolder(trunk, "folder")
    f1 = newFile(folder, "f1.txt")
    val folder1 = newFolder(folder, "folder1")
    f2 = newFile(folder1, "f2.txt")

    importAndCheckOut(trunk, branchFolder)
  }

  private fun importAndCheckOut(trunk: File, branch: File = myBranchVcsRoot) {
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.absolutePath, myTrunkUrl)
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myTrunkUrl, myBranchUrl)

    FileUtil.delete(trunk)
    checkOutFile(myTrunkUrl, trunk)
    checkOutFile(myBranchUrl, branch)
  }

  private fun editAndCommit(trunk: File, file: File, content: String = CONTENT1) {
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

    editAndCommit(trunk, vf!!, content)
  }

  private fun editAndCommit(trunk: File, file: VirtualFile, content: String) {
    editFileInCommand(file, content)
    commitFile(trunk)
  }

  private fun editFile(file: File) {
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

    editFileInCommand(vf!!, CONTENT1)
  }

  private fun assertMergeInfo(file: File, expectedValue: String) {
    val propertyValue = vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), MERGE_INFO, false, Revision.WORKING)

    assertNotNull(propertyValue)
    assertEquals(expectedValue, propertyValue!!.toString())
  }

  private fun assertMergeResult(changeLists: List<SvnChangeList>, vararg mergeResults: MergeCheckResult) {
    myOneShotMergeInfoHelper.prepare()

    for ((index, mergeResult) in mergeResults.withIndex()) {
      assertMergeResult(changeLists[index], mergeResult)
      assertMergeResultOneShot(changeLists[index], mergeResult)
    }
  }

  private fun assertMergeResult(changeList: SvnChangeList, mergeResult: MergeCheckResult) =
    assertEquals(mergeResult, myMergeChecker.checkList(changeList, myBranchVcsRoot.absolutePath))

  private fun assertMergeResultOneShot(changeList: SvnChangeList, mergeResult: MergeCheckResult) =
    assertEquals(mergeResult, myOneShotMergeInfoHelper.checkList(changeList))

  private fun commitFile(file: File) = runInAndVerifyIgnoreOutput("ci", "-m", "test", file.absolutePath)
  private fun updateFile(file: File) = runInAndVerifyIgnoreOutput("up", file.absolutePath)
  private fun checkOutFile(url: String, directory: File) = runInAndVerifyIgnoreOutput("co", url, directory.absolutePath)
  private fun setMergeInfo(file: File, value: String) = runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", value, file.absolutePath)
  private fun merge(file: File, url: String, vararg revisions: String) = merge(file, url, false, *revisions)
  private fun recordMerge(file: File, url: String, vararg revisions: String) = merge(file, url, true, *revisions)
  private fun merge(file: File, url: String, recordOnly: Boolean, vararg revisions: String) {
    val parameters = mutableListOf<String>("merge", *revisions, url, file.absolutePath)
    if (recordOnly) {
      parameters.add("--record-only")
    }

    runInAndVerifyIgnoreOutput(*parameters.toTypedArray())
  }
}
