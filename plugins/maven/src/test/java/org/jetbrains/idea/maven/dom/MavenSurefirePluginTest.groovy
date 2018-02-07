/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.idea.maven.MavenCustomRepositoryHelper

/**
 * @author Sergey Evdokimov
 */
class MavenSurefirePluginTest extends MavenDomTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setRepositoryPath(new MavenCustomRepositoryHelper(myDir, "plugins").getTestDataPath("plugins"));
  }

  void testCompletion() {
    importProject("""
  <groupId>simpleMaven</groupId>
  <artifactId>simpleMaven</artifactId>
  <packaging>jar</packaging>
  <version>1.0</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <additionalClasspathElements>
            <additionalClasspathElement>\${basedir}/src/<caret></additionalClasspathElement>
          </additionalClasspathElements>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    createProjectSubFile("src/main/A.txt", "")
    createProjectSubFile("src/test/A.txt", "")
    createProjectSubFile("src/A.txt", "")

    assertCompletionVariants(myProjectPom, "main", "test")
  }

  void testCompletionSurefireProperties() {
    importProject("""
  <groupId>simpleMaven</groupId>
  <artifactId>simpleMaven</artifactId>
  <version>1.0</version>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <additionalClasspathElements>
            <additionalClasspathElement>\${surefire.<caret>}</additionalClasspathElement>
          </additionalClasspathElements>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    assertCompletionVariants(myProjectPom, "surefire.forkNumber", "surefire.threadNumber")
  }

  void testCompletionSurefirePropertiesOutsideConfiguration() {
    importProject("""
  <groupId>simpleMaven</groupId>
  <artifactId>simpleMaven</artifactId>
  <version>1.0</version>

  <properties>
    <aaa>\${surefire.<caret>}</aaa>
  </properties>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    assertCompletionVariants(myProjectPom)
  }

  void testSurefirePropertiesHighlighting() {
    importProject("""
  <groupId>simpleMaven</groupId>
  <artifactId>simpleMaven</artifactId>
  <version>1.0</version>

    <properties>
    <aaa>\${surefire.forkNumber}</aaa>
  </properties>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <additionalClasspathElements>
            <additionalClasspathElement>\${surefire.forkNumber}</additionalClasspathElement>
          </additionalClasspathElements>
        </configuration>

        <executions>
          <execution>
            <goals>
              <goal>test</goal>
              <goal>\${surefire.threadNumber}</goal>
            </goals>
            <configuration>
              <debugForkedProcess>\${surefire.threadNumber}</debugForkedProcess>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
""")

    createProjectPom("""
  <groupId>simpleMaven</groupId>
  <artifactId>simpleMaven</artifactId>
  <version>1.0</version>

    <properties>
    <aaa>\${<error>surefire.forkNumber</error>}</aaa>
  </properties>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <additionalClasspathElements>
            <additionalClasspathElement>\${surefire.forkNumber}</additionalClasspathElement>
          </additionalClasspathElements>
        </configuration>

        <executions>
          <execution>
            <goals>
              <goal>test</goal>
              <goal>\${<error>surefire.threadNumber</error>}</goal>
            </goals>
            <configuration>
              <debugForkedProcess>\${surefire.threadNumber}</debugForkedProcess>
            </configuration>
          </execution>
        </executions>

      </plugin>
    </plugins>
  </build>
""")

    checkHighlighting()
  }


}
