/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.sql

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import gnu.trove.THashSet
import gnu.trove.TObjectHashingStrategy
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy

/**
 * @author Sergey Evdokimov
 */
class MavenSqlInjectionTest extends LightCodeInsightFixtureTestCase {

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

  <packaging>jar</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>sql-maven-plugin</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <id>groovy-magic</id>
            <phase>package</phase>
            <goals>
              <goal>execute</goal>
            </goals>
            <configuration>
              <sqlCommand>
                <caret>
              </sqlCommand>
            </configuration>
          </execution>
        </executions>
        <dependencies>
          <dependency>
            <groupId>org.apache.ant</groupId>
            <artifactId>ant-nodeps</artifactId>
            <version>1.8.0</version>
          </dependency>
        </dependencies>
      </plugin>
    </plugins>
  </build>

</project>
""")

    myFixture.completeBasic()

    def lookups = myFixture.lookupElementStrings
    lookups = new THashSet<String>(lookups, CaseInsensitiveStringHashingStrategy.INSTANCE)
    assert lookups.containsAll(["select", "update", "delete"])
  }

}
