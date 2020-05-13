// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.pipenv.PipenvKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
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

  // PY-27337
  public void testPackageInExtrasRequire() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("a.py");
    configureInspection();
  }

  // PY-30803
  public void testPipEnvEnvironmentMarkers() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    final VirtualFile pipFileLock = myFixture.findFileInTempDir("Pipfile.lock");
    assertNotNull(pipFileLock);
    final PyPackageManager packageManager = PyPackageManager.getInstance(getProjectDescriptor().getSdk());
    final List<PyRequirement> requirements = PipenvKt.getPipFileLockRequirements(pipFileLock, packageManager);
    final List<String> names = StreamEx.of(requirements).map(PyRequirement::getName).toList();
    assertNotEmpty(names);
    assertContainsElements(names, "atomicwrites", "attrs", "more-itertools", "pluggy", "py", "pytest", "six");
    assertDoesntContain(names, "pathlib2");
  }

  // PY-41106
  public void testSuppressingRequirementWithExtras() {
    myFixture.configureByText("requirements.txt", "<warning descr=\"Package requirement 'pkg[extras]' is not satisfied\">pkg[extras]</warning>");

    final PyPackageRequirementsInspection inspection = new PyPackageRequirementsInspection();
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());

    myFixture.launchAction(myFixture.findSingleIntention("Ignore requirement"));
    assertContainsElements(inspection.ignoredPackages, "pkg");
  }

  // PY-41106
  public void testIgnoredRequirementWithExtras() {
    myFixture.configureByText("requirements.txt", "pkg[extras]");

    final PyPackageRequirementsInspection inspection = new PyPackageRequirementsInspection();
    inspection.ignoredPackages.add("pkg");

    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }
}
