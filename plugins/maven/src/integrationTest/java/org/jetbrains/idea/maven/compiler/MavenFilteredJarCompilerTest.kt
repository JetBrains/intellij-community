// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.compiler

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.MAVEN_COMPILER_PROPERTIES
import org.jetbrains.idea.maven.fixtures.assertDoesNotExist
import org.jetbrains.idea.maven.fixtures.assertExists
import org.jetbrains.idea.maven.fixtures.compileModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenFilteredJarCompilerTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun shouldCompileAdditionalJar() = runBlocking() {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      $MAVEN_COMPILER_PROPERTIES
      <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.1.2</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                        <configuration>
                            <classifier>only-interfaces</classifier>
                            <skipIfEmpty>true</skipIfEmpty>
                            <includes>
                                <include>org/service/**</include>
                            </includes>
                            <excludes>
                                <exclude>org/service/impl/**</exclude>
                            </excludes>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    """.trimIndent())

    maven.createProjectSubFile("src/main/java/org/service/MyService.java", """
      package org.service;

      public interface MyService {
          String doStuff();
      }
    """.trimIndent())

    maven.createProjectSubFile("src/main/java/org/service/impl/MyServiceImpl.java", """
      package org.service.impl;
      import org.service.MyService;
      
      public class MyServiceImpl implements MyService {
          @Override
          public String doStuff() {
              return "I am a class from the library";
          }
      }
    """.trimIndent())


    maven.compileModules("project")

    maven.assertExists("target/classes/org/service/MyService.class")
    maven.assertExists("target/classes/org/service/impl/MyServiceImpl.class")

    maven.assertExists("target/classes-jar-only-interfaces/org/service/MyService.class")
    maven.assertDoesNotExist("target/classes-jar-only-interfaces/org/service/impl/MyServiceImpl.class")
  }

  @Test
  fun shouldProcessResourcesForAdditionalResourceDirectories() = runBlocking() {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      $MAVEN_COMPILER_PROPERTIES
      <build>
        <plugins>
           <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>add-test-fixtures</id>
                        <phase>generate-test-sources</phase>
                        <goals>
                            <goal>add-test-source</goal>
                            <goal>add-test-resource</goal>
                        </goals>
                        <configuration>
                            <sources>
                                <source>src/testFixtures/java</source>
                            </sources>
                            <resources>
                                <resource>
                                    <directory>src/testFixtures/resources</directory>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>src/test/**</exclude>
                        <exclude>src/it/**</exclude>
                    </excludes>
                </configuration>
                <executions>
                    <execution>
                        <id>test-fixtures</id>
                        <goals>
                            <goal>test-jar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    """.trimIndent())
    maven.createProjectSubFile("src/testFixtures/java/org/service/MyTestFixture.java", """
      package org.service;
        public class MyTestFixture {
      }
    """.trimIndent())

    maven.createProjectSubFile("src/testFixtures/resources/docker/mongo.properties", "mongo=mongo:6.0.6\n")


    maven.compileModules("project")

    maven.assertExists("target/test-classes/org/service/MyTestFixture.class")
    maven.assertExists("target/test-classes/docker/mongo.properties")

    maven.assertExists("target/test-classes-jar-tests/org/service/MyTestFixture.class")
    maven.assertExists("target/test-classes-jar-tests/docker/mongo.properties")
  }

}