// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext;

import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.intellij.restructuredtext.parsing.RestParserDefinition;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

/**
 * User : catherine
 */
@TestApplication
public class RestParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PythonHelpersLocator.getPythonCommunityPath() + "/../plugins/restructuredtext/testData/psi/", "rst", false,
                   new RestParserDefinition());

  @BeforeEach
  void setUp() {
    parsingFixture.get().setCheckAllPsiRoots(false);
  }

  @Test
  public void testTitle() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testInjection() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testReference() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testReferenceTarget() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSubstitution() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testDirectiveWithNewLine() {
    parsingFixture.get().doTest(true);
  }
}
