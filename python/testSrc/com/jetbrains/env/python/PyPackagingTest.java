// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python;

import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

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
