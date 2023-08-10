// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.IconTestUtil;
import com.intellij.util.PsiIconUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class PyPackageIconTest extends PyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(getTestName(false), "");
    RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
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
    VirtualFile found = myFixture.findFileInTempDir(path);
    assertNotNull(found);
    PsiDirectory dir = PsiManager.getInstance(myFixture.getProject()).findDirectory(found);
    assertNotNull(dir);
    Icon icon = PsiIconUtil.getProvidersIcon(dir, 0);
    assertEquals(AllIcons.Nodes.Package.equals(IconTestUtil.unwrapIcon(icon)), has);
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
