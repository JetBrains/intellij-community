/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.application.options.RegistryManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyRelativeImportResolveTest extends PyMultiFileResolveTestCase {
  private static final String PLAIN_DIR = "plainDirectory";
  private static final String NAMESPACE_PACK_DIR = "namespacePackage";
  private static final String ORDINARY_PACK_DIR = "ordinaryPackage";

  private String myNamespacePackageDirectory = null;

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/multiFile/relativeAndSameDirectoryImports/";
  }

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PyTestCase.ourPy3Descriptor;
  }

  @Override
  protected void prepareTestDirectory() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    if (myNamespacePackageDirectory != null) {
      toggleNamespacePackageDirectory(myNamespacePackageDirectory);
    }
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myNamespacePackageDirectory = null;
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.getLatest());
    RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
      myNamespacePackageDirectory = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
      super.tearDown();
    }
  }

  public void testOrdinaryPackageDottedRelativeFromImport() {
    doTestOrdinaryPackageFromImportOfFunction();
  }

  public void testOrdinaryPackageSameDirectoryFromImport() {
    doTestOrdinaryPackageFromImportOfFunction();
  }

  private void doTestOrdinaryPackageFromImportOfFunction() {
    myTestFileName = ORDINARY_PACK_DIR + "/mod.py";
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testNamespacePackageDottedRelativeImport() {
    doTestNamespacePackageImportOfModule();
  }

  public void testNamespacePackageSameDirectoryImport() {
    doTestNamespacePackageImportOfModule();
  }

  private void doTestNamespacePackageImportOfModule() {
    myTestFileName = NAMESPACE_PACK_DIR + "/mod.py";
    assertResolvesTo(PyFile.class, "util.py");
  }

  public void testNestedNamespacePackageDottedRelativeImport() {
    doTestNestedNamespacePackageImportOfModule();
  }

  public void testNestedNamespacePackageSameDirectoryImport() {
    doTestNestedNamespacePackageImportOfModule();
  }

  private void doTestNestedNamespacePackageImportOfModule() {
    myTestFileName = NAMESPACE_PACK_DIR + "/nestedNamespacePackage/mod.py";
    myNamespacePackageDirectory = NAMESPACE_PACK_DIR;
    assertResolvesTo(PyFile.class, "util.py");
  }

  public void testPlainDirectoryDottedRelativeImport() {
    doTestPlainDirectoryImportOfModule();
  }

  public void testPlainDirectorySameDirectoryImport() {
    doTestPlainDirectoryImportOfModule();
  }

  private void doTestPlainDirectoryImportOfModule() {
    myTestFileName = PLAIN_DIR + "/mod.py";
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testPlainDirectoryDottedRelativeImportRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    doTestPlainDirectoryImportOfModule();
  }


  public void testPlainDirectorySameDirectoryImportRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    myTestFileName = PLAIN_DIR + "/mod.py";
    assertUnresolved();
  }

  private void toggleNamespacePackageDirectory(@NotNull String directory) {
    PyNamespacePackagesService
      .getInstance(myFixture.getModule())
      .toggleMarkingAsNamespacePackage(myFixture.findFileInTempDir(directory));
  }
}
