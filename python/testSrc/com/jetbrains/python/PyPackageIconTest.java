// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.RegistryManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.PlatformIcons;
import com.intellij.util.PsiIconUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class PyPackageIconTest extends PyTestCase {
  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(getTestName(false), "");
    RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
  }

  @Override
  public void tearDown() throws Exception {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
    super.tearDown();
  }

  // PY-38642
  // upd: PY-42750
  public void testNamespacePackage() {
    toggleNamespacePackageDirectory("nspkg");
    checkPackageIcon("nspkg", true);
    checkPackageIcon("nspkg/subpkg", true);
  }

  public void testNamespacePackageRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    toggleNamespacePackageDirectory("nspkg");
    checkPackageIcon("nspkg", false);
    checkPackageIcon("nspkg/subpkg", false);
  }

  public void testPlainDirectoryInsideOldStylePackage() {
    checkPackageIcon("pkg", true);
    checkPackageIcon("pkg/plainDirectory", false);
  }

  public void testNamespacePackageInsideOldStylePackage() {
    toggleNamespacePackageDirectory("pkg/nspkg");
    checkPackageIcon("pkg", true);
    checkPackageIcon("pkg/nspkg", true);
  }

  public void testNamespacePackageInsideOldStylePackageRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    toggleNamespacePackageDirectory("pkg/nspkg");
    checkPackageIcon("pkg", true);
    checkPackageIcon("pkg/nspkg", false);
  }

  public void testOldStylePackage() {
    checkPackageIcon("pkg", true);
    checkPackageIcon("pkg/subpkg", true);
  }

  public void testOldStylePackageRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    checkPackageIcon("pkg", true);
    checkPackageIcon("pkg/subpkg", true);
  }

  public void testOldStylePackageWithIllegalName() {
    checkPackageIcon("illegally named", true);
    checkPackageIcon(".pkg", true);
  }

  public void testOldStylePackageInsideNamespacePackage() {
    toggleNamespacePackageDirectory("nspkg");
    checkPackageIcon("nspkg", true);
    checkPackageIcon("nspkg/pkg", true);
  }

  public void testOldStylePackageInsideNamespacePackageRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    toggleNamespacePackageDirectory("nspkg");
    checkPackageIcon("nspkg", false);
    checkPackageIcon("nspkg/pkg", true);
  }

  public void testOldStylePackageInsideSourceRoot() {
    final List<VirtualFile> roots = Collections.singletonList(myFixture.findFileInTempDir("src"));
    runWithSourceRoots(roots, () -> {
      checkPackageIcon("src", false);
      checkPackageIcon("src/pkg", true);
    });
  }

  public void testDirectoryInOrdinaryPackageInNamespacePackage() {
    toggleNamespacePackageDirectory("nspkg");
    checkPackageIcon("nspkg", true);
    checkPackageIcon("nspkg/pkg", true);
    checkPackageIcon("nspkg/pkg/directory", false);
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

  private void toggleNamespacePackageDirectory(@NotNull String directory) {
    PyNamespacePackagesService
      .getInstance(myFixture.getModule())
      .toggleMarkingAsNamespacePackage(myFixture.findFileInTempDir(directory));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/packageIcon";
  }
}
