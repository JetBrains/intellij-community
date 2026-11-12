/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.pyi;

import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.allure.Layers;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestDataPath("$CONTENT_ROOT/../testData/pyi/parsing")
@Subsystems.Parsing
@Layers.Functional
@TestApplication
public class PyiParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PythonTestUtil.getTestDataPath() + "/pyi/parsing", "pyi", false,
                   new PyiParserDefinition(), new PythonParserDefinition());

  private void doTest() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSimple() {
    doTest();
    assertInstanceOf(PyiFile.class, parsingFixture.get().getFile());
    final PyiFile pyiFile = (PyiFile)parsingFixture.get().getFile();
    assertEquals(LanguageLevel.getLatest(), ReadAction.computeBlocking(() -> pyiFile.getLanguageLevel()));
  }
}
