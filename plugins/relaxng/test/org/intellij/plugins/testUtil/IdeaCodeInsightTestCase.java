package org.intellij.plugins.testUtil;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 27.03.2008
*/
public interface IdeaCodeInsightTestCase {
  String getTestDataPath();
  CodeInsightTestFixture getFixture();
}