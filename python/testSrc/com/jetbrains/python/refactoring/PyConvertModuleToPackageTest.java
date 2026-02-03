package com.jetbrains.python.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.refactoring.convertModulePackage.PyConvertModuleToPackageAction;

/**
 * @author Mikhail Golubev
 */
public class PyConvertModuleToPackageTest extends PyTestCase {

  // PY-4387
  public void testSimple() throws Exception {
    final String rootBeforePath = getTestName(true) + "/before";
    final String rootAfterPath = getTestName(true) + "/after";
    final VirtualFile copiedDirectory = myFixture.copyDirectoryToProject(rootBeforePath, "");
    myFixture.configureByFile("a.py");

    final PyFile moduleToConvert = assertInstanceOf(myFixture.getFile(), PyFile.class);
    new PyConvertModuleToPackageAction().createPackageFromModule(moduleToConvert);

    PlatformTestUtil.assertDirectoriesEqual(copiedDirectory, getVirtualFileByName(getTestDataPath() +rootAfterPath));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/convertModuleToPackage/";
  }
}
