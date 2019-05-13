package com.jetbrains.python.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.convertModulePackage.PyConvertPackageToModuleAction;

/**
 * @author Mikhail Golubev
 */
public class PyConvertPackageToModuleTest extends PyTestCase {

  // PY-4387
  public void testSimple() throws Exception {
    final String rootBeforePath = getTestName(true) + "/before";
    final String rootAfterPath = getTestName(true) + "/after";
    final VirtualFile copiedDirectory = myFixture.copyDirectoryToProject(rootBeforePath, "");

    final VirtualFile directory = assertInstanceOf(myFixture.findFileInTempDir("a"), VirtualFile.class);
    final PsiDirectory packageToConvert = PsiManager.getInstance(myFixture.getProject()).findDirectory(directory);
    assertNotNull(packageToConvert);
    new PyConvertPackageToModuleAction().createModuleFromPackage(packageToConvert);

    PlatformTestUtil.assertDirectoriesEqual(copiedDirectory, getVirtualFileByName(getTestDataPath() +rootAfterPath));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/convertPackageToModule/";
  }
}
