// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.setRawPomFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenNoRootDefinedInspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.isDirectory

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenNoRootDefinedInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.fixture.enableInspections(MavenNoRootDefinedInspection::class.java)
  }

  @Test
  fun testHighlightingWithMvn() = runBlocking {
    maven.importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    maven.createProjectSubDir(".mvn")
    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingWithoutMvn() = runBlocking {
    maven.assumeModel_4_1_0("applicable for model version 4.1.0")
    maven.importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())

    maven.setRawPomFile("""<?xml version="1.0"?>
       <warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"><</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">project</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"> </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns="http://maven.apache.org/POM/4.1.0"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">></warning>
        <modelVersion>4.1.0</modelVersion>
      <groupId>my.group</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version></project>
    """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testHighlightingWithRoot() = runBlocking {
    maven.assumeModel_4_1_0("only for model 4.1.0")
    maven.importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())

    val modelVersion = maven.modelVersion
    maven.setRawPomFile("""
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
    maven.checkHighlighting()
  }


  @Test
  fun testRootTagQuickFix() = runBlocking {
    maven.assumeModel_4_1_0("applicable for model version 4.1.0")
    maven.importProjectAsync("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    val mvnDir = maven.projectRoot.toNioPath().resolve(".mvn")
    assertFalse(mvnDir.isDirectory(), "Directory should not exist!")

    maven.setRawPomFile("""<?xml version="1.0"?>
       <warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"><</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">project</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"> </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns="http://maven.apache.org/POM/4.1.0"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">
               </warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model"><caret>xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"</warning><warning descr="The root directory is not defined. Add the root=\"true\" attribute on the root project's model">></warning>
        <modelVersion>4.1.0</modelVersion>
      <groupId>my.group</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version></project>
    """.trimIndent())
    maven.checkHighlighting()
    val intention =  maven.fixture.availableIntentions.singleOrNull{it.text.contains("Add root")}
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)

    maven.fixture.checkResult("""<?xml version="1.0"?>
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