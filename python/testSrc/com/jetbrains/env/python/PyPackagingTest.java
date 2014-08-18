package com.jetbrains.env.python;

import com.google.common.collect.Sets;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author vlan
 */
public class PyPackagingTest extends PyEnvTestCase {
  @Override
  public void runPythonTest(PyTestTask testTask) {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows as after deleting from created virtualenvs original interpreter got spoiled
    }

    super.runPythonTest(testTask);
  }

  public void testGetPackages() {
    runPythonTest(new PyPackagingTestTask() {
      @Override
      public void runTestOn(String sdkHome) throws Exception {
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);
        List<PyPackage> packages = null;
        try {
          packages = ((PyPackageManagerImpl)PyPackageManager.getInstance(sdk)).getPackages();
        }
        catch (PyExternalProcessException e) {
          final int retcode = e.getRetcode();
          if (retcode != PyPackageManagerImpl.ERROR_NO_PIP && retcode != PyPackageManagerImpl.ERROR_NO_SETUPTOOLS) {
            fail(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()));
          }
        }
        if (packages != null) {
          assertTrue(packages.size() > 0);
          for (PyPackage pkg : packages) {
            assertTrue(pkg.getName().length() > 0);
          }
        }
      }
    });
  }

  public void testCreateVirtualEnv() {
    runPythonTest(new PyPackagingTestTask() {
      @Override
      public void runTestOn(String sdkHome) throws Exception {
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);
        try {
          final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
          // virtualenv >= 0.10 supports Python >= 2.6
          if (languageLevel.isOlderThan(LanguageLevel.PYTHON26)) {
            return;
          }
          final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
          final File venvDir = new File(tempDir, "venv");
          final String venvSdkHome = ((PyPackageManagerImpl)PyPackageManagerImpl.getInstance(sdk)).createVirtualEnv(venvDir.toString(),
                                                                                                                    false);
          final Sdk venvSdk = createTempSdk(venvSdkHome, SdkCreationType.EMPTY_SDK);
          assertNotNull(venvSdk);
          assertTrue(PythonSdkType.isVirtualEnv(venvSdk));
          assertInstanceOf(PythonSdkFlavor.getPlatformIndependentFlavor(venvSdk.getHomePath()), VirtualEnvSdkFlavor.class);
          final List<PyPackage> packages = ((PyPackageManagerImpl)PyPackageManagerImpl.getInstance(venvSdk)).getPackages();
          final PyPackage setuptools = findPackage("setuptools", packages);
          assertNotNull(setuptools);
          assertEquals("setuptools", setuptools.getName());
          assertEquals(PyPackageManagerImpl.SETUPTOOLS_VERSION, setuptools.getVersion());
          final PyPackage pip = findPackage("pip", packages);
          assertNotNull(pip);
          assertEquals("pip", pip.getName());
          assertEquals(PyPackageManagerImpl.PIP_VERSION, pip.getVersion());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        catch (PyExternalProcessException e) {
          throw new RuntimeException(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()), e);
        }
      }
    });
  }

  public void testInstallPackage() {
    runPythonTest(new PyPackagingTestTask() {

      @Override
      public void runTestOn(String sdkHome) throws Exception {
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);
        try {
          final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
          final File venvDir = new File(tempDir, "venv");
          final String venvSdkHome = ((PyPackageManagerImpl)PyPackageManager.getInstance(sdk)).createVirtualEnv(venvDir.getPath(), false);
          final Sdk venvSdk = createTempSdk(venvSdkHome, SdkCreationType.EMPTY_SDK);
          assertNotNull(venvSdk);
          final PyPackageManagerImpl manager = (PyPackageManagerImpl)PyPackageManager.getInstance(venvSdk);
          final List<PyPackage> packages1 = manager.getPackages();
          // TODO: Install Markdown from a local file
          manager.install(list(PyRequirement.fromString("Markdown<2.2"),
                               new PyRequirement("httplib2")), Collections.<String>emptyList());
          final List<PyPackage> packages2 = manager.getPackages();
          final PyPackage markdown2 = findPackage("Markdown", packages2);
          assertNotNull(markdown2);
          assertTrue(markdown2.isInstalled());
          final PyPackage pip1 = findPackage("pip", packages1);
          assertNotNull(pip1);
          assertEquals("pip", pip1.getName());
          assertEquals(PyPackageManagerImpl.PIP_VERSION, pip1.getVersion());
          manager.uninstall(list(pip1));
          final List<PyPackage> packages3 = manager.getPackages();
          final PyPackage pip2 = findPackage("pip", packages3);
          assertNull(pip2);
        }
        catch (PyExternalProcessException e) {
          new RuntimeException(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()), e);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Nullable
  private static PyPackage findPackage(String name, List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (name.equals(pkg.getName())) {
        return pkg;
      }
    }
    return null;
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }


  private abstract static class PyPackagingTestTask extends PyExecutionFixtureTestTask {
    @Override
    public Set<String> getTags() {
      return Sets.newHashSet("packaging");
    }
  }
}
