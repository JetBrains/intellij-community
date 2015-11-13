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
package org.jetbrains.idea.maven.plugins;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;

/**
 * @author Sergey Evdokimov
 */
public class MavenParameterGoalTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void tearDown() throws Exception {
    MavenServerManager.getInstance().shutdown(true);
    super.tearDown();
  }

  public void testCompletion() {
    myFixture.configureByText("pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                         "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                                         "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                         "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                                         "  <modelVersion>4.0.0</modelVersion>\n" +
                                         "\n" +
                                         "  <groupId>simpleMaven</groupId>\n" +
                                         "  <artifactId>simpleMaven</artifactId>\n" +
                                         "  <version>1.0</version>\n" +
                                         "\n" +
                                         "  <build>\n" +
                                         "    <plugins>\n" +
                                         "      <plugin>\n" +
                                         "        <groupId>org.apache.maven.plugins</groupId>\n" +
                                         "        <artifactId>maven-changelog-plugin</artifactId>\n" +
                                         "        <configuration>\n" +
                                         "          <goal><caret></goal>\n" +
                                         "        </configuration>\n" +
                                         "      </plugin>\n" +
                                         "    </plugins>\n" +
                                         "  </build>\n" +
                                         "\n" +
                                         "</project>\n");

    myFixture.completeBasic();

    assertContainsElements(myFixture.getLookupElementStrings(), "clean", "compile", "package");
  }

}
