// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.fixtures.PyProjectStructureTestUtilKt;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.packaging.common.PythonPackage;
import com.jetbrains.python.packaging.management.RequirementsProviderType;
import com.jetbrains.python.packaging.management.TestPythonPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.pipenv.PipEnvParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.packaging.management.TestPythonPackageManagerService.replacePyPiPackageCacheService;
import static com.jetbrains.python.packaging.management.TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance;


@Subsystems.Inspections
@Layers.Functional
public class PyPackageRequirementsInspectionTest extends PyInspectionTestCase {
  private void setRequirementsProvider(@NotNull RequirementsProviderType type) {
    final PythonInterpreter interpreter = PyProjectStructureTestUtilKt.findPythonInterpreterForTests(myFixture.getModule());
    assertNotNull(interpreter);
    TestPythonPackageManager.setRequirementsProvider(interpreter, type);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyPackageRequirementsInspection.class;
  }


  @Override
  public void setUp() throws Exception {
    super.setUp();
    // The legacy flow gets a new SDK of the light project for each test, and the requirements file is next to the module.
    PyProjectStructureTestUtilKt.ownModuleSdkForTests(myFixture.getModule());
    var cachedPackages = List.of("opster", "clevercss", "django", "test3", "pyzmq", "markdown", "pytest", "django-simple-captcha");

    replacePyPiPackageCacheService(myFixture.getProject(), cachedPackages);
    replacePythonPackageManagerServiceWithTestInstance(myFixture.getProject(), List.of());
    PyProjectStructureTestUtilKt.awaitPythonInterpreters(myFixture.getProject());
  }

  public void testPartiallySatisfiedRequirementsTxt() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  public void testPartiallySatisfiedSetupPy() {
    setRequirementsProvider(RequirementsProviderType.SETUP_PY);
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("test1.py");
    configureInspection();
  }

  public void testPartiallySatisfiedEnvironmentYml() {
    setRequirementsProvider(RequirementsProviderType.ENVIRONMENT_YML);
    doMultiFileTest("test1.py");
  }

  public void testImportsNotInRequirementsTxt() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  public void testImportsNotInEnvironmentYml() {
    setRequirementsProvider(RequirementsProviderType.ENVIRONMENT_YML);
    doMultiFileTest("test1.py");
  }

  public void testDuplicateInstallAndTests() {
    setRequirementsProvider(RequirementsProviderType.SETUP_PY);
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("test1.py");
    configureInspection();
  }

  // PY-16753
  public void testIpAddressNotInRequirements() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doMultiFileTest("test1.py"));
  }

  // PY-17422
  public void testTypingNotInRequirements() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doMultiFileTest("test1.py"));
  }

  // PY-26725
  public void testSecretsNotInRequirements() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doMultiFileTest("test1.py"));
  }

  // PY-11963
  // PY-26050
  public void testMismatchBetweenPackageAndRequirement() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  public void testOnePackageManyPossibleRequirements() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  // PY-20489
  public void testPackageInstalledIntoModule() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest();
  }

  // PY-27337
  public void testPackageInExtrasRequire() {
    setRequirementsProvider(RequirementsProviderType.SETUP_PY);
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("a.py");
    configureInspection();
  }

  // PY-30803
  public void testPipEnvEnvironmentMarkers() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    final VirtualFile pipFileLock = myFixture.findFileInTempDir("Pipfile.lock");
    assertNotNull(pipFileLock);
    final List<PyRequirement> requirements = PipEnvParser.getPipFileLockRequirements(pipFileLock);
    final List<String> names = ContainerUtil.map(requirements, PyRequirement::getName);
    assertNotEmpty(names);
    assertContainsElements(names, "atomicwrites", "attrs", "more-itertools", "pathlib2", "pluggy", "py", "pytest", "six");
  }

  // PY-41106
  public void testIgnoredRequirementWithExtras() {

    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    myFixture.configureByText("requirements.txt", "pkg[extras]");

    final PyPackageRequirementsInspection inspection = new PyPackageRequirementsInspection();
    inspection.ignoredPackages.add("pkg");

    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  // PY-54850
  public void testRequirementMismatchWarningDisappearsOnInstall() {
    setRequirementsProvider(RequirementsProviderType.REQUIREMENTS_TXT);
    PythonPackage zopeInterfacePackage = new PythonPackage("zope.interface", "5.4.0", false);

    replacePythonPackageManagerServiceWithTestInstance(myFixture.getProject(), Collections.singletonList(zopeInterfacePackage));

    doMultiFileTest("a.py");
  }
}