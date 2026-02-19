// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.inspections.MavenModulesInMaven4Inspection
import org.junit.Test

class MavenModulesInMaven4InspectionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()
    fixture.enableInspections(MavenModulesInMaven4Inspection::class.java)
  }

  @Test
  fun shouldHighlight() = runBlocking {
    assumeModel_4_1_0("applicable only for model 4.1.0")
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>test</version>
""")

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>test</version>
""")
    createModulePom("m1/sub1", """
      <groupId>test</groupId>
      <artifactId>sub1</artifactId>
      <version>test</version>
""")
    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>test</version>
      <modules>
        <module>m1</module>
        <module>m2</module>
        <module>m1/sub1</module>
      </modules>
""")
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>test</version>
      <warning descr="${MavenDomBundle.message("inspection.modules.tag.in.maven.4")}"><modules>
        <module>m1</module>
        <module>m2</module>
        <module>m1/sub1</module>
      </modules><caret></warning>
""")
    checkHighlighting()

    val intention = fixture.availableIntentions.singleOrNull { it.text == MavenDomBundle.message("inspection.modules.tag.in.maven.4.name") }
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult("""<?xml version="1.0"?>
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