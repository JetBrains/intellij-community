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

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.Test;

public class MavenParameterGoalTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      MavenServerManager.getInstance().closeAllConnectorsAndWait();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testCompletion() {
    myFixture.configureByText("pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <build>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-changelog-plugin</artifactId>
              <configuration>
                <goal><caret></goal>
              </configuration>
            </plugin>
          </plugins>
        </build>

      </project>
      """);

    myFixture.completeBasic();

    assertContainsElements(myFixture.getLookupElementStrings(), "clean", "compile", "package");
  }

}
