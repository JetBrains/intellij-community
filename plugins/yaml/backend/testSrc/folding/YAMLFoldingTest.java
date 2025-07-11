// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.folding;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class YAMLFoldingTest extends BasePlatformTestCase {

  public static final String RELATIVE_TEST_DATA_PATH = "/plugins/yaml/backend/testData/org/jetbrains/yaml/";
  public static final String TEST_DATA_PATH = PathManagerEx.getCommunityHomePath() + RELATIVE_TEST_DATA_PATH;

  public void testFolding() {
    withFoldingSettings()
      .execute(() -> defaultTest());
  }

  public void testSequenceFolding() {
    withFoldingSettings()
      .execute(() -> defaultTest());
  }

  public void testRuby18677() {
    withFoldingSettings()
      .execute(() -> defaultTest());
  }

  public void testRuby22423() {
    withFoldingSettings()
      .execute(() -> defaultTest());
  }

  public void testComments() {
    withFoldingSettings()
      .execute(() -> defaultTest());
  }

  public void testRegionFolding() {
    withFoldingSettings()
      .execute(() -> defaultTest());
  }

  public void testAbbreviationLimit1() {
    withFoldingSettings()
      .withAbbreviationLengthLimit(1)
      .execute(() -> defaultTest());
  }

  public void testAbbreviationLimit2() {
    withFoldingSettings()
      .withAbbreviationLengthLimit(2)
      .execute(() -> defaultTest());
  }

  public void testAbbreviationLimit8() {
    withFoldingSettings()
      .withAbbreviationLengthLimit(8)
      .execute(() -> defaultTest());
  }

  public void testNoAbbreviation() {
    withFoldingSettings()
      .withUseAbbreviation(false)
      .execute(() -> defaultTest());
  }

  public void defaultTest() {
    myFixture.testFolding(getTestDataPath() + getTestName(true) + ".yaml");
  }

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_PATH + "/folding/data/";
  }

  protected static class YamlFoldingSettingsBuilder {
    private boolean useAbbreviation = true;
    private int abbreviationLengthLimit = 20;

    public YamlFoldingSettingsBuilder withUseAbbreviation(boolean value) {
      this.useAbbreviation = value;
      return this;
    }

    public YamlFoldingSettingsBuilder withAbbreviationLengthLimit(int value) {
      this.abbreviationLengthLimit = value;
      return this;
    }

    public void execute(ThrowableRunnable<? extends RuntimeException> runnable) {
      YAMLFoldingSettings settings = YAMLFoldingSettings.getInstance();
      boolean originalUseAbbreviation = settings.useAbbreviation;
      int originalAbbreviationLengthLimit = settings.abbreviationLengthLimit;
      try {
        settings.useAbbreviation = useAbbreviation;
        settings.abbreviationLengthLimit = abbreviationLengthLimit;
        runnable.run();
      }
      finally {
        settings.useAbbreviation = originalUseAbbreviation;
        settings.abbreviationLengthLimit = originalAbbreviationLengthLimit;
      }
    }
  }

  protected YamlFoldingSettingsBuilder withFoldingSettings() {
    return new YamlFoldingSettingsBuilder();
  }

  @FunctionalInterface
  public interface ThrowableRunnable<E extends Exception> {
    void run() throws E;
  }
}
