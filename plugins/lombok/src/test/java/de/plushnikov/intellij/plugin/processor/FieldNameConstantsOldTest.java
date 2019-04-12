package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.File;

/**
 * Unit tests for @FieldNameConstants annotation from old version of lombok
 */
public class FieldNameConstantsOldTest extends AbstractLombokParsingTestCase {

  private static final String OLD_LOMBOK_SRC_PATH = "./old";

  @Override
  public void setUp() throws Exception {
    VfsRootAccess.allowRootAccess(new File(getTestDataPath(), getBasePath()).getCanonicalPath(),
      new File(OLD_LOMBOK_SRC_PATH).getCanonicalPath());

    super.setUp();
  }

  public void testFieldnameconstants$FieldNameConstantsOldBasic() {
    super.loadLombokFilesFrom(OLD_LOMBOK_SRC_PATH);
    doTest(true);
  }

  @Override
  protected boolean acceptLombokFile(String javaFilePath) {
    return !javaFilePath.endsWith("FieldNameConstants.java") || javaFilePath.startsWith(".\\old");
  }
}
