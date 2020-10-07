// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

/**
 * Ensures resolve is always case-sensitive, even in Windows (see pep 235)
 *
 * @author Ilya.Kazakevich
 */
public final class PyCaseSensitiveResolveTest extends PyEnvTestCase {
  @Test
  public void testCaseSensitive() {
    runPythonTest(new PyExecutionFixtureTestTask(null) {
      @NotNull
      @Override
      protected String getTestDataPath() {
        return super.getTestDataPath() + "/resolveTest";
      }

      @Override
      public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) throws Exception {
        createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
        ApplicationManager.getApplication().invokeAndWait(() -> {
          myFixture.copyDirectoryToProject("", "");
          myFixture.configureByFile("test.py");
          myFixture.completeBasic();
          Assert.assertEquals("Completion failed. Failed to resolve unittest? Case-sensitive resolve failed?",
                              "from unittest import TestCase\n" +
                              '\n' +
                              '\n' +
                              "TestCase().assertIsNotNone()",
                              myFixture.getFile().getText());
        });
      }
    });
  }
}
