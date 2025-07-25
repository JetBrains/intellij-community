// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.packaging.PyPIPackageCache;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.packaging.common.PythonPackage;
import com.jetbrains.python.packaging.management.RequirementsProviderType;
import com.jetbrains.python.packaging.management.TestPythonPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.SdksKt;
import com.jetbrains.python.sdk.pipenv.PipEnvParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.packaging.management.TestPythonPackageManagerService.replacePythonPackageManagerServiceWithTestInstance;


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
    SdksKt.setAssociationToModuleAsync(sdk, myFixture.getModule());
    assertNotNull(sdk);

    PyPIPackageCache.reload(List.of("opster", "clevercss", "django", "test3", "pyzmq", "markdown", "pytest", "django-simple-captcha"));
    replacePythonPackageManagerServiceWithTestInstance(myFixture.getProject(), List.of());
  }

  public void testPartiallySatisfiedRequirementsTxt() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  public void testPartiallySatisfiedSetupPy() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.SETUP_PY);
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("test1.py");
    configureInspection();
  }

  public void testPartiallySatisfiedEnvironmentYml() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.ENVIRONMENT_YML);
    doMultiFileTest("test1.py");
  }

  public void testImportsNotInRequirementsTxt() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  public void testImportsNotInEnvironmentYml() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.ENVIRONMENT_YML);
    doMultiFileTest("test1.py");
  }

  public void testDuplicateInstallAndTests() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.SETUP_PY);
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    myFixture.configureFromTempProjectFile("test1.py");
    configureInspection();
  }

  // PY-16753
  public void testIpAddressNotInRequirements() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doMultiFileTest("test1.py"));
  }

  // PY-17422
  public void testTypingNotInRequirements() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doMultiFileTest("test1.py"));
  }

  // PY-26725
  public void testSecretsNotInRequirements() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doMultiFileTest("test1.py"));
  }

  // PY-11963
  // PY-26050
  public void testMismatchBetweenPackageAndRequirement() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  public void testOnePackageManyPossibleRequirements() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest("test1.py");
  }

  // PY-20489
  public void testPackageInstalledIntoModule() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    doMultiFileTest();
  }

  // PY-27337
  public void testPackageInExtrasRequire() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.SETUP_PY);
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

    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    myFixture.configureByText("requirements.txt", "pkg[extras]");

    final PyPackageRequirementsInspection inspection = new PyPackageRequirementsInspection();
    inspection.getIgnoredPackages().add("pkg");

    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  // PY-54850
  public void testRequirementMismatchWarningDisappearsOnInstall() {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    sdk.putUserData(TestPythonPackageManager.REQUIREMENTS_PROVIDER_KEY, RequirementsProviderType.REQUIREMENTS_TXT);
    PythonPackage zopeInterfacePackage = new PythonPackage("zope.interface", "5.4.0", false);

    replacePythonPackageManagerServiceWithTestInstance(myFixture.getProject(), Collections.singletonList(zopeInterfacePackage));

    doMultiFileTest("a.py");
  }
}