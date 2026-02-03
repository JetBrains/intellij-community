// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenNoRootDefinedInspection
import org.junit.Test
import kotlin.io.path.isDirectory

class MavenNoRootDefinedInspectionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()

    fixture.enableInspections(MavenNoRootDefinedInspection::class.java)
  }

  @Test
  fun testHighlightingWithMvn() = runBlocking {
    importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    createProjectSubDir(".mvn")
    checkHighlighting()
  }

  @Test
  fun testHighlightingWithoutMvn() = runBlocking {
    assumeModel_4_1_0("applicable for model version 4.1.0")
    importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())

    setRawPomFile("""<?xml version="1.0"?>
       <warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"><</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">project</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"> </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns="http://maven.apache.org/POM/4.1.0"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">></warning>
        <modelVersion>4.1.0</modelVersion>
      <groupId>my.group</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version></project>
    """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingWithRoot() = runBlocking {
    assumeModel_4_1_0("only for model 4.1.0")
    importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())

    setRawPomFile("""
      <?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/$modelVersion"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/$modelVersion http://maven.apache.org/xsd/maven-$modelVersion.xsd"
               root="true">
        <modelVersion>$modelVersion</modelVersion>
      <groupId>my.group</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version></project>
    """.trimIndent())
    checkHighlighting()
  }


  @Test
  fun testRootTagQuickFix() = runBlocking {
    assumeModel_4_1_0("applicable for model version 4.1.0")
    importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    val mvnDir = projectRoot.toNioPath().resolve(".mvn")
    assertFalse("Directory should not exist!", mvnDir.isDirectory())

    setRawPomFile("""<?xml version="1.0"?>
       <warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"><</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">project</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"> </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns="http://maven.apache.org/POM/4.1.0"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"><caret>xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">></warning>
        <modelVersion>4.1.0</modelVersion>
      <groupId>my.group</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version></project>
    """.trimIndent())
    checkHighlighting()
    val intention =  fixture.availableIntentions.singleOrNull{it.text.contains("Add root")}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult("""<?xml version="1.0"?>
       <project xmlns="http://maven.apache.org/POM/4.1.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"
                root="true">
        <modelVersion>4.1.0</modelVersion>
      <groupId>my.group</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version></project>
    """.trimIndent())
  }


}