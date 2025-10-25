// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenNewModelVersionInOldMavenInspection
import org.junit.Test

class MavenNewModelVersionWithOldMavenInspectionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()

    fixture.enableInspections(MavenNewModelVersionInOldMavenInspection::class.java)
  }

  @Test
  fun testCheckNoHighlightingInNewMaven() = runBlocking {
    assumeModel_4_1_0("not applicable for old model version")
    importProjectAsync("""
       <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>test</version>
""")
    checkHighlighting()
  }

  @Test
  fun testCheckHighlightingInOldMaven() = runBlocking {
    assumeMaven3()
    importProjectAsync("""
       <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>test</version>
""")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion><error descr="Maven version 4+ is required for projects with 4.1.0 schema">4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()
  }

}