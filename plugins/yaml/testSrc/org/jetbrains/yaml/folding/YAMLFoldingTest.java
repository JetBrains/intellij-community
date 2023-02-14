// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.folding;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class YAMLFoldingTest extends BasePlatformTestCase {

  public static final String RELATIVE_TEST_DATA_PATH = "/plugins/yaml/testSrc/org/jetbrains/yaml/";
  public static final String TEST_DATA_PATH = PathManagerEx.getCommunityHomePath() + RELATIVE_TEST_DATA_PATH;

  public void testFolding() {
    defaultTest();
  }

  public void testSequenceFolding() {
    defaultTest();
  }

  public void testRuby18677() {
    defaultTest();
  }

  public void testRuby22423() {
    defaultTest();
  }
  
  public void testComments() {
    defaultTest();
  }

  public void testRegionFolding() {
    defaultTest();
  }

  public void defaultTest() {
    myFixture.testFolding(getTestDataPath() + getTestName(true) + ".yaml");
  }

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_PATH + "/folding/data/";
  }
} 