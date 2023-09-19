// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.junit.Test;

import java.io.IOException;

public class MavenSurefirePluginTest extends MavenDomWithIndicesTestCase {
  @Override
  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir.toPath(), myProject, "plugins", "local1");
  }

  @Test
  public void testCompletion() throws IOException {
    configureProjectPom(
      """
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
                    <additionalClasspathElement>${basedir}/src/<caret></additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """);
    importProject();

    createProjectSubFile("src/main/A.txt", "");
    createProjectSubFile("src/test/A.txt", "");
    createProjectSubFile("src/A.txt", "");

    assertCompletionVariants(myProjectPom, "main", "test");
  }

  @Test
  public void testCompletionSurefireProperties() {
    configureProjectPom(
      """
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
                    <additionalClasspathElement>${surefire.<caret>}</additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """);
    importProject();

    assertCompletionVariants(myProjectPom, "surefire.forkNumber", "surefire.threadNumber");
  }

  @Test
  public void testCompletionSurefirePropertiesOutsideConfiguration() {
    configureProjectPom(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

          <properties>
            <aaa>${surefire.<caret>}</aaa>
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
        """);
    importProject();

    assertCompletionVariants(myProjectPom);
  }

  @Test
  public void testSurefirePropertiesHighlighting() {
    importProject(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

            <properties>
            <aaa>${surefire.forkNumber}</aaa>
          </properties>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                  <additionalClasspathElements>
                    <additionalClasspathElement>${surefire.forkNumber}</additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>

                <executions>
                  <execution>
                    <goals>
                      <goal>test</goal>
                      <goal>${surefire.threadNumber}</goal>
                    </goals>
                    <configuration>
                      <debugForkedProcess>${surefire.threadNumber}</debugForkedProcess>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        """);

    resolvePlugins();

    createProjectPom(
      """
          <groupId>simpleMaven</groupId>
          <artifactId>simpleMaven</artifactId>
          <version>1.0</version>

            <properties>
            <aaa>${<error descr="Cannot resolve symbol 'surefire.forkNumber'">surefire.forkNumber</error>}</aaa>
          </properties>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                  <additionalClasspathElements>
                    <additionalClasspathElement>${surefire.forkNumber}</additionalClasspathElement>
                  </additionalClasspathElements>
                </configuration>

                <executions>
                  <execution>
                    <goals>
                      <goal>test</goal>
                      <goal>${<error descr="Cannot resolve symbol 'surefire.threadNumber'">surefire.threadNumber</error>}</goal>
                    </goals>
                    <configuration>
                      <debugForkedProcess>${surefire.threadNumber}</debugForkedProcess>
                    </configuration>
                  </execution>
                </executions>

              </plugin>
            </plugins>
          </build>
        """);

    checkHighlighting();
  }
}
