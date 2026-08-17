// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.envVar
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.HighlightPointer
import org.jetbrains.idea.maven.fixtures.assertHighlighted
import org.jetbrains.idea.maven.fixtures.assertSearchResults
import org.jetbrains.idea.maven.fixtures.assertSearchResultsInclude
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.fixtures.search
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPropertyFindUsagesTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testFindModelPropertyFromReference() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.version}</name>
                       <description>${'$'}{project.version}</description>
                       """.trimIndent())

    maven.assertSearchResults(maven.projectPom,
                              maven.findTag("project.name"),
                              maven.findTag("project.description"))
  }

  @Test
  fun testFindModelPropertyFromReferenceWithDifferentQualifiers() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>version}</name>
                       <description>${'$'}{pom.version}</description>
                       """.trimIndent())

    maven.assertSearchResults(maven.projectPom,
                              maven.findTag("project.name"),
                              maven.findTag("project.description"))
  }

  @Test
  fun testFindUsagesFromTag() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <<caret>version>1</version>
                       <name>${'$'}{project.version}</name>
                       <description>${'$'}{version}</description>
                       """.trimIndent())

    maven.assertSearchResults(maven.projectPom,
                              maven.findTag("project.name"),
                              maven.findTag("project.description"))
  }

  @Test
  fun testFindUsagesFromTagValue() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1<caret>1</version>
                       <name>${'$'}{project.version}</name>
                       """.trimIndent())

    maven.assertSearchResults(maven.projectPom, maven.findTag("project.name"))
  }

  @Test
  fun testFindUsagesFromProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>11</version>
                       <name>${'$'}{foo}</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    maven.assertSearchResultsInclude(maven.projectPom, maven.findTag("project.name"))
  }

  @Test
  fun testFindUsagesForEnvProperty() = runBlocking {
    val envVar = maven.envVar
    maven.updateProjectPom("""
  <groupId>test</groupId>
  <artifactId>module1</artifactId>
  <version>11</version>
  <name>${"$"}{env.<caret>${envVar}}</name>
  <description>${"$"}{env.${envVar}}</description>
  """.trimIndent())

    maven.assertSearchResultsInclude(maven.projectPom, maven.findTag("project.name"), maven.findTag("project.description"))
  }

  @Test
  fun testFindUsagesForSystemProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>11</version>
                       <name>${'$'}{use<caret>r.home}</name>
                       <description>${'$'}{user.home}</description>
                       """.trimIndent())

    maven.assertSearchResultsInclude(maven.projectPom, maven.findTag("project.name"), maven.findTag("project.description"))
  }

  @Test
  fun testFindUsagesForSystemPropertyInFilteredResources() = runBlocking {
    maven.createProjectSubDir("res")

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>
                    <name>${'$'}{user.home}</name>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())
    maven.updateAllProjects()

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=abc\${user<caret>.home}abc")

    val result = maven.search(f)
    val expected = readAction { MavenDomUtil.findPropertyValue(maven.project, f, "foo") }
    assertContain(result, maven.findTag("project.name"), expected)
  }

  @Test
  fun testHighlightingFromTag() = runBlocking {
    maven.updateProjectPom($$"""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version><caret>1</version>
                       <name>${project.version}</name>
                       <description>${version}</description>
                       """.trimIndent())

    maven.assertHighlighted(maven.projectPom,
                            HighlightPointer(maven.findTag("project.name"), "project.version"),
                            HighlightPointer(maven.findTag("project.description"), "version"))
  }
}
