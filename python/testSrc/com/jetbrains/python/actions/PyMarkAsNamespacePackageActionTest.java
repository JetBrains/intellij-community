// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.namespacePackages.PyMarkAsNamespacePackageAction;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyMarkAsNamespacePackageActionTest extends PyTestCase {
  private static final String PLAIN_DIR = "plainDirectory";
  private static final String NAMESPACE_PACK_DIR = "namespacePackage";
  private static final String ORDINARY_PACK_DIR = "ordinaryPackage";

  private PyNamespacePackagesService myNspService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myNspService = PyNamespacePackagesService.getInstance(myFixture.getModule());
  }

  public void testPlainDirectory() {
    doCopyDirectory();

    Presentation presentation = doLaunchAction(PLAIN_DIR);
    assertTrue(presentation.isEnabledAndVisible());
    assertEquals(presentation.getText(), PyBundle.message("python.namespace.package.folder"));
    assertTrue(myNspService.isMarked(myFixture.findFileInTempDir(PLAIN_DIR)));

    presentation = doLaunchAction(PLAIN_DIR);
    assertTrue(presentation.isEnabledAndVisible());
    assertEquals(presentation.getText(), PyBundle.message("python.unmark.as.namespace.package"));
    assertFalse(myNspService.isMarked(myFixture.findFileInTempDir(PLAIN_DIR)));
  }

  public void testOrdinaryPackage() {
    doCopyDirectory();

    Presentation presentation = doLaunchAction(ORDINARY_PACK_DIR);
    assertTrue(presentation.isVisible());
    assertFalse(presentation.isEnabled());
    assertFalse(myNspService.isMarked(myFixture.findFileInTempDir(ORDINARY_PACK_DIR)));
  }

  public void testNestedNamespacePackage() {
    doCopyDirectory();
    myNspService.toggleMarkingAsNamespacePackage(myFixture.findFileInTempDir(NAMESPACE_PACK_DIR));
    assertTrue(myNspService.isMarked(myFixture.findFileInTempDir(NAMESPACE_PACK_DIR)));
    assertTrue(myNspService.isNamespacePackage(myFixture.findFileInTempDir(NAMESPACE_PACK_DIR + "/nestedNamespacePackage")));

    Presentation presentation = doLaunchAction(NAMESPACE_PACK_DIR + "/nestedNamespacePackage");
    assertTrue(presentation.isVisible());
    assertFalse(presentation.isEnabled());
  }

  public void testPlainDirectoryInOrdinaryPackage() {
    doCopyDirectory();

    Presentation presentation = doLaunchAction(ORDINARY_PACK_DIR + "/nestedPlainDirectory");
    assertTrue(presentation.isEnabledAndVisible());
    assertEquals(presentation.getText(), PyBundle.message("python.namespace.package.folder"));
    assertTrue(myNspService.isMarked(myFixture.findFileInTempDir(ORDINARY_PACK_DIR + "/nestedPlainDirectory")));

    presentation = doLaunchAction(ORDINARY_PACK_DIR + "/nestedPlainDirectory");
    assertTrue(presentation.isEnabledAndVisible());
    assertEquals(presentation.getText(), PyBundle.message("python.unmark.as.namespace.package"));
    assertFalse(myNspService.isMarked(myFixture.findFileInTempDir(ORDINARY_PACK_DIR + "/nestedPlainDirectory")));
  }

  public void testSourceRoot() {
    doCopyDirectory();

    runWithSourceRoots(List.of(myFixture.findFileInTempDir("sourceRoot")), () -> {
      Presentation presentation = doLaunchAction("sourceRoot");
      assertTrue(presentation.isVisible());
      assertFalse(presentation.isEnabled());
    });
  }

  public void testPython2PlainDirectory() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doCopyDirectory();
      Presentation presentation = doLaunchAction(PLAIN_DIR);
      assertFalse(presentation.isVisible());
      assertFalse(presentation.isEnabled());
    });
  }

  public void testNotProjectDirectory() {
    final String libRootPath = getTestDataPath() + "/" + getTestName(false);
    final VirtualFile libRoot = StandardFileSystems.local().findFileByPath(libRootPath);
    runWithAdditionalClassEntryInSdkRoots(libRoot, () -> {
      VirtualFile libPlainDirectory = libRoot.findChild("plainDirectory");
      Presentation presentation = doLaunchAction(libPlainDirectory);
      assertTrue(presentation.isVisible());
      assertFalse(presentation.isEnabled());
    });
  }

  private @NotNull Presentation doLaunchAction(@NotNull String directoryPath) {
    return doLaunchAction(myFixture.findFileInTempDir(directoryPath));
  }

  private @NotNull Presentation doLaunchAction(@NotNull VirtualFile directory) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[] {directory})
      .add(CommonDataKeys.PROJECT, myFixture.getProject())
      .add(PlatformCoreDataKeys.MODULE, myFixture.getModule())
      .build();

    AnAction action = new PyMarkAsNamespacePackageAction();
    AnActionEvent e = TestActionEvent.createTestEvent(action, dataContext);
    ActionUtil.updateAction(action, e);
    if (e.getPresentation().isEnabledAndVisible()) {
      ActionUtil.performAction(action, e);
    }

    return e.getPresentation();
  }

  private void doCopyDirectory() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/actions/MarkAsNamespacePackage";
  }
}