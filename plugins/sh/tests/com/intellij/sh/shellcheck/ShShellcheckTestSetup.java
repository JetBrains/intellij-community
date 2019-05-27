// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import junit.extensions.TestSetup;
import junit.framework.Test;

import java.io.File;

public class ShShellcheckTestSetup extends TestSetup {
  public ShShellcheckTestSetup(Test test) {
    super(test);
  }

  @Override
  protected void tearDown() {
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) return;

    File testDir = new File(ShShellcheckTestUtil.getShellcheckTestDir());
    if (!testDir.exists()) return;

    FileUtil.delete(testDir);
  }
}
