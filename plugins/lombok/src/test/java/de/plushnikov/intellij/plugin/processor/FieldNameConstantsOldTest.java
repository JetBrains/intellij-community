package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.util.PathUtil;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Unit tests for @FieldNameConstants annotation from old version of lombok
 */
public class FieldNameConstantsOldTest extends AbstractLombokParsingTestCase {

  private static final String LOMBOK_SRC_PATH = "./generated/src/lombok";
  private static final String OLD_LOMBOK_SRC_PATH = "./old";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final Disposable projectDisposable = myFixture.getProjectDisposable();
    final String basePath = new File(getTestDataPath(), getBasePath()).getCanonicalPath();
    VfsRootAccess.allowRootAccess(projectDisposable, basePath, new File(LOMBOK_SRC_PATH).getCanonicalPath());
    VfsRootAccess.allowRootAccess(projectDisposable, basePath, new File(OLD_LOMBOK_SRC_PATH).getCanonicalPath());

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testFieldnameconstants$FieldNameConstantsOldBasic() {
    loadLombokFilesFrom(LOMBOK_SRC_PATH);
    loadLombokFilesFrom(OLD_LOMBOK_SRC_PATH);
    doTest(true);
  }

  private void loadLombokFilesFrom(final String srcPath) {
    List<File> filesByMask = FileUtil.findFilesByMask(Pattern.compile(".*\\.java"), new File(srcPath));
    filesByMask.stream().map(File::getPath).filter(this::acceptLombokFile).map(PathUtil::toSystemIndependentName).forEach(
      filePath -> myFixture.copyFileToProject(filePath, filePath.substring(srcPath.lastIndexOf("/") + 1))
    );
  }

  private boolean acceptLombokFile(String javaFilePath) {
    return !javaFilePath.endsWith("FieldNameConstants.java") || javaFilePath.startsWith("." + File.separator + "old");
  }

  @Override
  protected void loadLombokLibrary() {
    // do nothing here
  }
}
