// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Test;

import java.io.File;

public class StructureImportingFsRefreshTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    MavenUtil.setNoBackgroundMode();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    RunAll.runAll(
      () -> super.tearDown(),
      () -> MavenUtil.resetNoBackgroundMode()
    );
  }

  @Test
  public void testRefreshFSAfterImport() {
    myProjectRoot.getChildren(); // make sure fs is cached
    new File(myProjectRoot.getPath(), "foo").mkdirs();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
    if (isNewImportingProcess) {
      PlatformTestUtil.waitForPromise(myImportingResult.getVfsRefreshPromise());
    }

    assertNotNull(myProjectRoot.findChild("foo"));
  }

}
