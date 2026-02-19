// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.UsefulTestCase;
import junit.extensions.TestSetup;
import junit.framework.Test;

import java.io.IOException;
import java.nio.file.Path;

public class ShShellcheckTestSetup extends TestSetup {
  public ShShellcheckTestSetup(Test test) {
    super(test);
  }

  @Override
  protected void tearDown() throws IOException {
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) return;

    Path testDir = ShShellcheckTestUtil.getShellcheckTestDir();
    NioFiles.deleteRecursively(testDir);
  }
}
