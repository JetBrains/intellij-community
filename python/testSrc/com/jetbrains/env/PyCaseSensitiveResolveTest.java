/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.env;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
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
    runTest(new PyExecutionFixtureTestTask(null) {
      @NotNull
      @Override
      protected String getTestDataPath() {
        return super.getTestDataPath() + "/resolveTest";
      }

      @Override
      public void runTestOn(@NotNull  final String sdkHome) throws Exception {
        createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS);
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
    }, getTestName(false));
  }
}
