// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenBomPackagingInOldSchema
import org.junit.Test

class MavenBomInOldModelInspectionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()

    fixture.enableInspections(MavenBomPackagingInOldSchema::class.java)
    runBlocking {
      importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>test</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    }
  }

  @Test
  fun testDoNotFireHighlightIn41() = runBlocking {
    assumeModel_4_1_0("")
    createProjectPom("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       <packaging>bom</packaging>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testFireHighlightIn40() = runBlocking {
    assumeModel_4_0_0("")
    setRawPomFile("""<?xml version="1.0"?>
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
    checkHighlighting()
  }

  @Test
  fun testUpdateToModel41() = runBlocking {
    assumeModel_4_0_0("")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion><error descr="Model version 4.1.0 is required for packaging bom">4.0.0</error></modelVersion>
  <groupId>my.group</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>
  <packaging><error descr="Model version 4.1.0 is required for packaging bom">bo<caret>m</error></packaging>  </project>
    """.trimIndent())
    checkHighlighting()
    val intention =  fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)
    fixture.checkResult("""<?xml version="1.0"?>
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