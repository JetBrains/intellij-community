package org.jetbrains.idea.svn;

/**
 * @author yole
 */
public class SvnDeleteTest extends SvnTestCase {
  // IDEADEV-16066
  /*@Test
  public void testDeletePackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");

    verify(runSvn("status"), "A child", "A child\\a.txt");
    checkin();

    deleteFileInCommand(dir);
    verify(runSvn("status"), "D child", "D child\\a.txt");

    LocalFileSystem.getInstance().refresh(false);

    final List<Change> changes = getAllChanges();
    Assert.assertEquals(2, changes.size());
  }*/
}