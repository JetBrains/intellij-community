// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.compiler

import com.intellij.maven.testFramework.MavenCompilingTestCase
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenFilteredJarCompilerTest : MavenCompilingTestCase() {

  @Test
  fun shouldCompileAdditionalJar() = runBlocking() {
    Registry.get("maven.build.additional.jars").setValue("true", getTestRootDisposable())
    importProjectAsync("""
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

    createProjectSubFile("src/main/java/org/service/MyService.java", """
      package org.service;

      public interface MyService {
          String doStuff();
      }
    """.trimIndent())

    createProjectSubFile("src/main/java/org/service/impl/MyServiceImpl.java", """
      package org.service.impl;
      import org.service.MyService;
      
      public class MyServiceImpl implements MyService {
          @Override
          public String doStuff() {
              return "I am a class from the library";
          }
      }
    """.trimIndent())


    compileModules("project")

    assertExists("target/classes/org/service/MyService.class")
    assertExists("target/classes/org/service/impl/MyServiceImpl.class")

    assertExists("target/classes-jar-only-interfaces/org/service/MyService.class")
    assertDoesNotExist("target/classes-jar-only-interfaces/org/service/impl/MyServiceImpl.class")
  }

}