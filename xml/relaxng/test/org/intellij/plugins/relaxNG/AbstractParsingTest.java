/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.relaxNG.compact.RncParserDefinition;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

public abstract class AbstractParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  protected final TestFixture<ParsingTestFixture> parsingFixture;

  public AbstractParsingTest(String s) {
    parsingFixture = parsingFixture(projectFixture, PlatformTestUtil.getCommunityPath() + "/xml/relaxng/testData/parsing" + "/" + "psi/" + s,
                                    "rnc", new RncParserDefinition());
  }
}
