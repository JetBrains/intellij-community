// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertHasPendingProjectForReload
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.scheduleProjectImportAndWait
import com.intellij.maven.testFramework.fixtures.type
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateProjectSubFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class CustomPomFileNameTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  @Test
  fun testCustomPomFileName() = runBlocking {
    maven.createProjectSubFile("m1/customName.xml", maven.createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project", maven.mn("project", "m1"))
  }

  @Test
  fun testFolderNameWithXmlExtension() = runBlocking {
    maven.createProjectSubFile("customName.xml/pom.xml", maven.createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>customName.xml</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project", maven.mn("project", "m1"))
  }

  @Test
  fun testModuleCompletion() = runBlocking {
    maven.createProjectSubFile("m1/customPom.xml", maven.createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customPom.xml</module>
                    </modules>
                    """.trimIndent())

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module><caret></module>
                       </modules>
                       """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "m1/customPom.xml")
  }

  @Test
  fun testParentCompletion() = runBlocking {
    maven.createProjectSubFile("m1/customPom.xml", maven.createPomXml(
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

    maven.createProjectSubFile("m1/m2/pom.xml", maven.createPomXml(
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

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customPom.xml</module>
                    </modules>
                    """.trimIndent())

    val m2 = maven.updateProjectSubFile("m1/m2/pom.xml", maven.createPomXml(
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

    maven.assertCompletionVariants(m2, "m2", "customPom.xml")
  }

  @Test
  fun testReimport() = runBlocking {
    maven.createProjectSubFile("m1/customName.xml", maven.createPomXml(
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

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """.trimIndent())

    maven.projectsManager.enableAutoImportInTests()

    val m1 = maven.updateProjectSubFile("m1/customName.xml", maven.createPomXml(
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
    maven.type(m1, '1')
    maven.assertHasPendingProjectForReload()

    maven.scheduleProjectImportAndWait()
  }

  @Test
  fun testCustomPomFileNamePom() = runBlocking {
    maven.createProjectSubFile("m1/customName.pom", maven.createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1/customName.pom</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project", maven.mn("project", "m1"))
  }
}
