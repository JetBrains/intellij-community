/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins


import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Sergey Evdokimov
 */
class MavenParameterFixedValueTest extends LightCodeInsightFixtureTestCase {

  void testCompletion() {
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
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <compilerReuseStrategy><caret></compilerReuseStrategy>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
""")

    myFixture.completeBasic()

    assertSameElements(['reuseCreated', 'reuseSame', 'alwaysNew'], myFixture.lookupElementStrings)
  }

}
