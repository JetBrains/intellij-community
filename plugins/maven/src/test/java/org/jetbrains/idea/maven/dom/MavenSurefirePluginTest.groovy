// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom


import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture

/**
 * @author Sergey Evdokimov
 */
class MavenSurefirePluginTest extends MavenDomWithIndicesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    setRepositoryPath(new MavenCustomRepositoryHelper(myDir, "plugins").getTestDataPath("plugins"))
    //need to recreate fixture
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject, "plugins", ['local1'] as String[])
    myIndicesFixture.setUp();
  }

  void testCompletion() {
    configureProjectPom("""
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
    importProject()

    createProjectSubFile("src/main/A.txt", "")
    createProjectSubFile("src/test/A.txt", "")
    createProjectSubFile("src/A.txt", "")

    assertCompletionVariants(myProjectPom, "main", "test")
  }

  void testCompletionSurefireProperties() {
    configureProjectPom("""
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
    importProject()

    assertCompletionVariants(myProjectPom, "surefire.forkNumber", "surefire.threadNumber")
  }

  void testCompletionSurefirePropertiesOutsideConfiguration() {
    configureProjectPom("""
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
    importProject()

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
    <aaa>\${<error descr="Cannot resolve symbol 'surefire.forkNumber'">surefire.forkNumber</error>}</aaa>
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
              <goal>\${<error descr="Cannot resolve symbol 'surefire.threadNumber'">surefire.threadNumber</error>}</goal>
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
