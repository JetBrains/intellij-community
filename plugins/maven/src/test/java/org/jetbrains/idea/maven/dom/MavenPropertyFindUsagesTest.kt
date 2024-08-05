// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    withContext(Dispatchers.EDT) {
      assertSearchResults(projectPom,
                          findTagBlocking("project.name"),
                          findTagBlocking("project.description"))
    }
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

    withContext(Dispatchers.EDT) {
      assertSearchResults(projectPom,
                          findTagBlocking("project.name"),
                          findTagBlocking("project.description"))
    }
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

    withContext(Dispatchers.EDT) {
      assertSearchResults(projectPom,
                          findTagBlocking("project.name"),
                          findTagBlocking("project.description"))
    }
  }

  @Test
  fun testFindUsagesFromTagValue() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module1</artifactId>
                       <version>1<caret>1</version>
                       <name>${'$'}{project.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertSearchResults(projectPom, findTagBlocking("project.name"))
    }
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

    withContext(Dispatchers.EDT) {
      assertSearchResultsInclude(projectPom, findTagBlocking("project.name"))
    }
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

    withContext(Dispatchers.EDT) {
      assertSearchResultsInclude(projectPom, findTagBlocking("project.name"), findTagBlocking("project.description"))
    }
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

    withContext(Dispatchers.EDT) {
      assertSearchResultsInclude(projectPom, findTagBlocking("project.name"), findTagBlocking("project.description"))
    }
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

    withContext(Dispatchers.EDT) {
      val result = search(f)
      assertContain(result, findTagBlocking("project.name"), MavenDomUtil.findPropertyValue(project, f, "foo"))
    }
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

    withContext(Dispatchers.EDT) {
      assertHighlighted(projectPom,
                        HighlightPointer(findTagBlocking("project.name"), "project.version"),
                        HighlightPointer(findTagBlocking("project.description"), "version"))
    }
  }
}
