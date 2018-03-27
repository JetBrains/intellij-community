// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspectionTest extends PyInspectionTestCase {
  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyPackageRequirementsInspection.class;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final Sdk sdk = PythonSdkType.findPythonSdk(myFixture.getModule());
    assertNotNull(sdk);
    PyPackageManager.getInstance(sdk).refreshAndGetPackages(true);
  }

  public void testPartiallySatisfiedRequirementsTxt() {
    doMultiFileTest("test1.py");
  }

  public void testPartiallySatisfiedSetupPy() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("test1.py");
    configureInspection();
  }

  public void testImportsNotInRequirementsTxt() {
    doMultiFileTest("test1.py");
  }

  public void testDuplicateInstallAndTests() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("test1.py");
    configureInspection();
  }

  // PY-16753
  public void testIpAddressNotInRequirements() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doMultiFileTest("test1.py"));
  }

  // PY-17422
  public void testTypingNotInRequirements() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doMultiFileTest("test1.py"));
  }

  // PY-26725
  public void testSecretsNotInRequirements() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doMultiFileTest("test1.py"));
  }

  // PY-11963
  // PY-26050
  public void testMismatchBetweenPackageAndRequirement() {
    doMultiFileTest("test1.py");
  }

  public void testOnePackageManyPossibleRequirements() {
    doMultiFileTest("test1.py");
  }

  // PY-20489
  public void testPackageInstalledIntoModule() {
    doMultiFileTest();
  }
}
