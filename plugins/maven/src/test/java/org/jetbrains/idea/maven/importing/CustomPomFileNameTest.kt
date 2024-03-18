// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CustomPomFileNameTest : MavenDomTestCase() {
  
  @Test
  fun testCustomPomFileName() = runBlocking {
    createProjectSubFile("m1/customName.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """.trimIndent())

    assertModules("project", mn("project", "m1"))
  }

  @Test
  fun testFolderNameWithXmlExtension() = runBlocking {
    createProjectSubFile("customName.xml/pom.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>customName.xml</module>
                    </modules>
                    """.trimIndent())

    assertModules("project", mn("project", "m1"))
  }

  @Test
  fun testModuleCompletion() = runBlocking {
    createProjectSubFile("m1/customPom.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customPom.xml</module>
                    </modules>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    assertCompletionVariants(projectPom, "m1/customPom.xml")
  }

  @Test
  fun testParentCompletion() = runBlocking {
    createProjectSubFile("m1/customPom.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <modules>
          <module>m2</module>
        </modules>
        """.trimIndent()))

    createProjectSubFile("m1/m2/pom.xml", createPomXml(
      """
        <artifactId>m2</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>m1</artifactId>
          <version>1</version>
          <relativePath>../customPom.xml</relativePath>
        </parent>
        """.trimIndent()))

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customPom.xml</module>
                    </modules>
                    """.trimIndent())

    val m2 = createProjectSubFile("m1/m2/pom.xml", createPomXml(
      """
        <artifactId>m2</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <relativePath>../<caret></relativePath>
        </parent>
        """.trimIndent()))

    assertCompletionVariants(m2, "m2", "customPom.xml")
  }

  @Test
  fun testReimport() = runBlocking {
    createProjectSubFile("m1/customName.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <dependencies>
          <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.0</version>
            <scope>test</scope>
          </dependency>
        </dependencies>
        """.trimIndent()))

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """.trimIndent())

    enableAutoReload()

    val m1 = createProjectSubFile("m1/customName.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <dependencies>
          <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.<caret>0</version>
            <scope>test</scope>
          </dependency>
        </dependencies>
        """.trimIndent()))
    type(m1, '1')
    assertHasPendingProjectForReload()

    scheduleProjectImportAndWait()
  }

  @Test
  fun testCustomPomFileNamePom() = runBlocking {
    createProjectSubFile("m1/customName.pom", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customName.pom</module>
                    </modules>
                    """.trimIndent())

    assertModules("project", mn("project", "m1"))
  }
}
