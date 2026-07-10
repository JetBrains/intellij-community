// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.inspections.MavenModulesInMaven4Inspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModulesInMaven4InspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.fixture.enableInspections(MavenModulesInMaven4Inspection::class.java)
  }

  @Test
  fun shouldHighlight() = runBlocking {
    maven.assumeModel_4_1_0("applicable only for model 4.1.0")
    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>test</version>
""")

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>test</version>
""")
    maven.createModulePom("m1/sub1", """
      <groupId>test</groupId>
      <artifactId>sub1</artifactId>
      <version>test</version>
""")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>test</version>
      <modules>
        <module>m1</module>
        <module>m2</module>
        <module>m1/sub1</module>
      </modules>
""")
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>test</version>
      <warning descr="${MavenDomBundle.message("inspection.modules.tag.in.maven.4")}"><modules>
        <module>m1</module>
        <module>m2</module>
        <module>m1/sub1</module>
      </modules><caret></warning>
""")
    maven.checkHighlighting()

    val intention = maven.fixture.availableIntentions.singleOrNull { it.text == MavenDomBundle.message("inspection.modules.tag.in.maven.4.name") }
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)

    maven.fixture.checkResult("""<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
  <modelVersion>4.1.0</modelVersion>

      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>test</version>
    <subprojects>
        <subproject>m1</subproject>
        <subproject>m2</subproject>
        <subproject>m1/sub1</subproject>
    </subprojects>
</project>""")
  }
}