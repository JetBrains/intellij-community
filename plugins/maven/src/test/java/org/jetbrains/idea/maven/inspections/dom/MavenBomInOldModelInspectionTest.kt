// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.setRawPomFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenBomPackagingInOldSchema
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenBomInOldModelInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.fixture.enableInspections(MavenBomPackagingInOldSchema::class.java)
    runBlocking {
      maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>test</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    }
  }

  @Test
  fun testDoNotFireHighlightIn41() = runBlocking {
    maven.assumeModel_4_1_0("")
    maven.createProjectPom("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       <packaging>bom</packaging>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testFireHighlightIn40() = runBlocking {
    val modelVersion = maven.modelVersion
    maven.assumeModel_4_0_0("")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/$modelVersion"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/$modelVersion http://maven.apache.org/xsd/maven-$modelVersion.xsd">
        <modelVersion><error descr="Model version 4.1.0 is required for packaging bom">4.0.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        <packaging><error descr="Model version 4.1.0 is required for packaging bom">bom</error></packaging>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testUpdateToModel41() = runBlocking {
    maven.assumeModel_4_0_0("")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion><error descr="Model version 4.1.0 is required for packaging bom">4.0.0</error></modelVersion>
  <groupId>my.group</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>
  <packaging><error descr="Model version 4.1.0 is required for packaging bom">bo<caret>m</error></packaging>  </project>
    """.trimIndent())
    maven.checkHighlighting()
    val intention =  maven.fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)
    maven.fixture.checkResult("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
  <modelVersion>4.1.0</modelVersion>
  <groupId>my.group</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>
  <packaging>bom</packaging>  </project>""".trimIndent())
  }
}