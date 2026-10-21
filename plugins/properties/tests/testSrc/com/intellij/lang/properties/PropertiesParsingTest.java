// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
public class PropertiesParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PluginPathManager.getPluginHomePath("properties") + "/tests/testData/propertiesFile/psi/", "properties",
                   new PropertiesParserDefinition());

  @Test
  public void testProp1() { parsingFixture.get().doTest(true); }

  @Test
  public void testComments() { parsingFixture.get().doTest(true); }

  @Test
  public void testHeader() { parsingFixture.get().doTest(true); }
}
