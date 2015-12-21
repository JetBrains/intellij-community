/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom

/**
 * @author Sergey Evdokimov
 */
class MavenDontCheckDependencyInManagementSectionTest extends MavenDomTestCase {

  public void testHighlighting() {
    importProject("""
<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>

  <dependencies>
    <dependency>
      <groupId>xxxx</groupId>
      <artifactId>yyyy</artifactId>
      <version>zzzz</version>
    </dependency>
  </dependencies>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>xxxx</groupId>
        <artifactId>yyyy</artifactId>
        <version>zzzz</version>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <plugins>
      <plugin>
        <groupId>xxxx</groupId>
        <artifactId>yyyy</artifactId>
        <version>zzzz</version>
      </plugin>
    </plugins>

    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>xxxx</groupId>
          <artifactId>yyyy</artifactId>
          <version>zzzz</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
""")

    createProjectPom("""
<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>

  <dependencies>
    <dependency>
      <groupId><error>xxxx</error></groupId>
      <artifactId><error>yyyy</error></artifactId>
      <version><error>zzzz</error></version>
    </dependency>
  </dependencies>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>xxxx</groupId>
        <artifactId>yyyy</artifactId>
        <version>zzzz</version>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <plugins>
      <plugin>
        <groupId><error>xxxx</error></groupId>
        <artifactId><error>yyyy</error></artifactId>
        <version><error>zzzz</error></version>
      </plugin>
    </plugins>

    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>xxxx</groupId>
          <artifactId>yyyy</artifactId>
          <version>zzzz</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
""")

    checkHighlighting(myProjectPom, true, false, true)
  }

}
