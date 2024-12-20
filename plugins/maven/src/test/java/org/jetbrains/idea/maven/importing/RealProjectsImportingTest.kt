// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RealProjectsImportingTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testImportStringBootStarter() = runBlocking {
    importProjectAsync("""
    |  <parent>
    |    <groupId>org.springframework.boot</groupId>
    |    <artifactId>spring-boot-starter-parent</artifactId>
    |    <version>3.0.0</version>
    |    <relativePath/>
    |  </parent>
    |  <groupId>test</groupId>
    |  <artifactId>project</artifactId>
    |  <version>0.0.1-SNAPSHOT</version>
    |  <packaging>jar</packaging>
    |  <dependencies>
    |    <dependency>
    |      <groupId>org.springframework.boot</groupId>
    |      <artifactId>spring-boot-starter</artifactId>
    |    </dependency>
    |  </dependencies>
    |
    """.trimMargin())

    assertModuleLibDeps("project",
                        "Maven: org.springframework.boot:spring-boot-starter:3.0.0",
                        "Maven: org.springframework.boot:spring-boot:3.0.0",
                        "Maven: org.springframework:spring-context:6.0.2",
                        "Maven: org.springframework:spring-aop:6.0.2",
                        "Maven: org.springframework:spring-beans:6.0.2",
                        "Maven: org.springframework:spring-expression:6.0.2",
                        "Maven: org.springframework.boot:spring-boot-autoconfigure:3.0.0",
                        "Maven: org.springframework.boot:spring-boot-starter-logging:3.0.0",
                        "Maven: ch.qos.logback:logback-classic:1.4.5",
                        "Maven: ch.qos.logback:logback-core:1.4.5",
                        "Maven: org.slf4j:slf4j-api:2.0.4",
                        "Maven: org.apache.logging.log4j:log4j-to-slf4j:2.19.0",
                        "Maven: org.apache.logging.log4j:log4j-api:2.19.0",
                        "Maven: org.slf4j:jul-to-slf4j:2.0.4",
                        "Maven: jakarta.annotation:jakarta.annotation-api:2.1.1",
                        "Maven: org.springframework:spring-core:6.0.2",
                        "Maven: org.springframework:spring-jcl:6.0.2",
                        "Maven: org.yaml:snakeyaml:1.33")
  }


  @Test
  fun testImportLog4Sl4j() = runBlocking {
    importProjectAsync("""
    |  <groupId>test</groupId>
    |  <artifactId>project</artifactId>
    |  <version>0.0.1-SNAPSHOT</version>
    |  <dependencies>
    |    <dependency>
    |      <groupId>org.apache.logging.log4j</groupId>
    |      <artifactId>log4j-to-slf4j</artifactId>
    |      <version>2.19.0</version>
    |    </dependency>
    |  </dependencies>
    |
    """.trimMargin())

    assertModuleLibDeps("project",
                        "Maven: org.apache.logging.log4j:log4j-to-slf4j:2.19.0",
                        "Maven: org.slf4j:slf4j-api:1.7.36",
                        "Maven: org.apache.logging.log4j:log4j-api:2.19.0")
  }

}
