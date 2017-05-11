package org.intellij.plugins.testUtil;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

public interface IdeaCodeInsightTestCase {
  String getTestDataPath();
  CodeInsightTestFixture getFixture();
}