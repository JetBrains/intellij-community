// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.readAction
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenPropertyFindUsagesTest : MavenDomTestCase() {

  override fun setUp() = runBlocking {
    super.setUp()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testFindModelPropertyFromReference() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.version}</name>
                       <description>${'$'}{project.version}</description>
                       """.trimIndent())

    assertSearchResults(projectPom,
                        findTag("project.name"),
                        findTag("project.description"))
  }

  @Test
  fun testFindModelPropertyFromReferenceWithDifferentQualifiers() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>version}</name>
                       <description>${'$'}{pom.version}</description>
                       """.trimIndent())

    assertSearchResults(projectPom,
                        findTag("project.name"),
                        findTag("project.description"))
  }

  @Test
  fun testFindUsagesFromTag() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <<caret>version>1</version>
                       <name>${'$'}{project.version}</name>
                       <description>${'$'}{version}</description>
                       """.trimIndent())

    assertSearchResults(projectPom,
                        findTag("project.name"),
                        findTag("project.description"))
  }

  @Test
  fun testFindUsagesFromTagValue() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1<caret>1</version>
                       <name>${'$'}{project.version}</name>
                       """.trimIndent())

    assertSearchResults(projectPom, findTag("project.name"))
  }

  @Test
  fun testFindUsagesFromProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>11</version>
                       <name>${'$'}{foo}</name>
                       <properties>
                         <f<caret>oo>value</foo>
                       </properties>
                       """.trimIndent())

    assertSearchResultsInclude(projectPom, findTag("project.name"))
  }

  @Test
  fun testFindUsagesForEnvProperty() = runBlocking {
    updateProjectPom("""
  <groupId>test</groupId>
  <artifactId>module1</artifactId>
  <version>11</version>
  <name>${"$"}{env.<caret>${envVar}}</name>
  <description>${"$"}{env.${envVar}}</description>
  """.trimIndent())

    assertSearchResultsInclude(projectPom, findTag("project.name"), findTag("project.description"))
  }

  @Test
  fun testFindUsagesForSystemProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>11</version>
                       <name>${'$'}{use<caret>r.home}</name>
                       <description>${'$'}{user.home}</description>
                       """.trimIndent())

    assertSearchResultsInclude(projectPom, findTag("project.name"), findTag("project.description"))
  }

  @Test
  fun testFindUsagesForSystemPropertyInFilteredResources() = runBlocking {
    createProjectSubDir("res")

    updateProjectPom("""
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
    updateAllProjects()

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=abc\${user<caret>.home}abc")

    val result = search(f)
    val expected = readAction { MavenDomUtil.findPropertyValue(project, f, "foo") }
    assertContain(result, findTag("project.name"), expected)
  }

  @Test
  fun testHighlightingFromTag() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version><caret>1</version>
                       <name>${'$'}{project.version}</name>
                       <description>${'$'}{version}</description>
                       """.trimIndent())

    assertHighlighted(projectPom,
                      HighlightPointer(findTag("project.name"), "project.version"),
                      HighlightPointer(findTag("project.description"), "version"))
  }
}
