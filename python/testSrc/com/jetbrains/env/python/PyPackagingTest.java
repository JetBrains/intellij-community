// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python;

import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyTargetEnvCreationManager;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf;
import static com.jetbrains.python.SdkUiUtilKt.isVirtualEnv;
import static org.junit.Assert.*;

public class PyPackagingTest extends PyEnvTestCase {
  @Override
  public void runPythonTest(PyTestTask testTask) {
    Assume.assumeFalse("Don't run under Windows as after deleting from created virtualenvs original interpreter got spoiled",
                       UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);
    super.runPythonTest(testTask);
  }

  @Test
  public void testGetPackages() {
    runPythonTest(new PyPackagingTestTask() {
      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);
        List<PyPackage> packages = null;
        try {
          packages = PyPackageManager.getInstance(sdk).refreshAndGetPackages(false);
        }
        catch (ExecutionException ignored) {
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

  @Test
  public void testCreateVirtualEnv() {
    runPythonTest(new PyPackagingTestTask() {
      @Override
      public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);
        try {
          final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
          final File venvDir = new File(tempDir, "venv");
          PyTargetEnvCreationManager manager = new PyTargetEnvCreationManager(sdk);
          final String venvSdkHome = manager.createVirtualEnv(venvDir.toString(),
                                                                                        false);
          final Sdk venvSdk = createTempSdk(venvSdkHome, SdkCreationType.EMPTY_SDK);
          assertNotNull(venvSdk);
          assertTrue(isVirtualEnv(venvSdk));
          assertInstanceOf(PythonSdkFlavor.getPlatformIndependentFlavor(venvSdk.getHomePath()), VirtualEnvSdkFlavor.class);
          final List<PyPackage> packages = PyPackageManager.getInstance(venvSdk).refreshAndGetPackages(false);
          final PyPackage setuptools = findPackage("setuptools", packages);
          assertNotNull(setuptools);
          assertEquals("setuptools", setuptools.getName());
          final PyPackage pip = findPackage("pip", packages);
          assertNotNull(pip);
          assertEquals("pip", pip.getName());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        catch (ExecutionException e) {
          throw new RuntimeException(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()), e);
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

  private abstract static class PyPackagingTestTask extends PyExecutionFixtureTestTask {
    PyPackagingTestTask() {
      super(null);
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      return Sets.newHashSet("packaging");
    }
  }
}
