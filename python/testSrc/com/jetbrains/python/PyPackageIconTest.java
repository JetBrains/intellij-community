// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.PlatformIcons;
import com.intellij.util.PsiIconUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class PyPackageIconTest extends PyTestCase {
  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(getTestName(false), "");
  }

  // PY-38642
  public void testNamespacePackage() {
    checkPackageIcon("nspkg", false);
    checkPackageIcon("nspkg/subpkg", false);
  }

  public void testOldStylePackage() {
    checkPackageIcon("pkg", true);
    checkPackageIcon("pkg/subpkg", true);
  }

  public void testOldStylePackageWithIllegalName() {
    checkPackageIcon("illegally named", false);
    checkPackageIcon(".pkg", false);
  }

  public void testOldStylePackageInsideNamespacePackage() {
    checkPackageIcon("nspkg", false);
    checkPackageIcon("nspkg/pkg", false);
  }

  public void testOldStylePackageInsideSourceRoot() {
    final List<VirtualFile> roots = Collections.singletonList(myFixture.findFileInTempDir("src"));
    runWithSourceRoots(roots, () -> {
      checkPackageIcon("src", false);
      checkPackageIcon("src/pkg", true);
    });
  }

  // PY-39274
  public void testStubPackage() {
    checkPackageIcon("stubpkg", true);
    checkPackageIcon("stubpkg/subpkg", true);
  }

  private void checkPackageIcon(@NotNull String path, boolean has) {
    final VirtualFile found = myFixture.findFileInTempDir(path);
    assertNotNull(found);
    final PsiDirectory dir = PsiManager.getInstance(myFixture.getProject()).findDirectory(found);
    assertNotNull(dir);
    final Icon icon = PsiIconUtil.getProvidersIcon(dir, 0);
    assertEquals(PlatformIcons.PACKAGE_ICON.equals(icon), has);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/packageIcon";
  }
}
