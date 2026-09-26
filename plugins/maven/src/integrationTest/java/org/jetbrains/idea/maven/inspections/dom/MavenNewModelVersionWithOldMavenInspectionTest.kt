// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.setRawPomFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenNewModelVersionInOldMavenInspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenNewModelVersionWithOldMavenInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.fixture.enableInspections(MavenNewModelVersionInOldMavenInspection::class.java)
  }

  @Test
  fun testCheckNoHighlightingInNewMaven() = runBlocking {
    maven.assumeModel_4_1_0("not applicable for old model version")
    maven.importProjectAsync("""
       <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>test</version>
""")
    maven.checkHighlighting()
  }

  @Test
  fun testCheckHighlightingInOldMaven() = runBlocking {
    maven.assumeMaven3()
    maven.importProjectAsync("""
       <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>test</version>
""")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion><error descr="Maven version 4+ is required for projects with 4.1.0 schema">4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

}