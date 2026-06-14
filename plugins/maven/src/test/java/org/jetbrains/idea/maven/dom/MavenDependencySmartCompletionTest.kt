// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDependencySmartCompletionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testCompletion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           ju<caret>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, maven.RENDERING_TEXT, "junit:junit")
  }



  @Test
  fun testInsertDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>juni<caret></dependency>
                       </dependencies>
                       """.trimIndent())

    maven.configTest(maven.projectPom)
    val elements = maven.fixture.completeBasic()
    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "junit:junit")
    UsefulTestCase.assertSize(1, elements)

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }


    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version><caret></version>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testInsertManagedDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>junit:<caret></dependency>
                       </dependencies>
                       """.trimIndent())

    maven.configTest(maven.projectPom)
    maven.fixture.complete(CompletionType.BASIC)
    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "junit:junit")
    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version>4.0</version>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testInsertManagedDependencyWithTypeAndClassifier() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitClassifier>sources</junitClassifier>
                         <junitType>test-jar</junitType>
                       </properties>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                             <type>${'$'}{junitType}</type>
                             <classifier>${'$'}{junitClassifier}</classifier>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>junit:<caret></dependency>
                       </dependencies>
                       """.trimIndent())

    maven.configTest(maven.projectPom)

    val elements = maven.fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }


    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <junitClassifier>sources</junitClassifier>
                                           <junitType>test-jar</junitType>
                                         </properties>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version>4.0</version>
                                               <type>${'$'}{junitType}</type>
                                               <classifier>${'$'}{junitClassifier}</classifier>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <type>${'$'}{junitType}</type>
                                               <classifier>${'$'}{junitClassifier}</classifier>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testCompletionArtifactIdThenVersion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>juni<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.refreshFiles(listOf(maven.projectPom))
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)

    var elements = maven.fixture.completeBasic()
    assertTrue(elements.size > 0)
    //assertEquals("junit:junit:3.8.1", elements[0].getLookupString())

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    elements = maven.fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)
    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version><caret></version>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))

    assertTrue(
      maven.fixture.getLookupElementStrings()!!.containsAll(mutableListOf("3.8.1", "4.0")))
  }

  @Test
  fun testCompletionArtifactIdThenGroupIdThenInsertVersion() = runBlocking {

    maven.createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>intellijartif<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.refreshFiles(listOf(maven.projectPom))
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)

    val elements = maven.fixture.completeBasic()

    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "intellijartifactanother", "intellijartifact")

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "org.intellijgroup")

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>org.intellijgroup</groupId>
                                               <artifactId>intellijartifactanother</artifactId>
                                               <version>1.0</version>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()))
  }

  @Test
  fun testCompletionArtifactIdNonExactmatch() = runBlocking {

    maven.createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>intellijmavent<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.refreshFiles(listOf(maven.projectPom))
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)
    val elements = maven.fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "org.example")
  }

  @Test
  fun testCompletionArtifactIdInsideManagedDependency() = runBlocking {

    maven.createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencyManagement>
                           <dependencies>
                               <dependency>
                                   <artifactId>intellijmavente<caret></artifactId>
                               </dependency>
                           </dependencies>
                       </dependencyManagement>
                       """.trimIndent())

    maven.refreshFiles(listOf(maven.projectPom))
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)

    val elements = maven.fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)
    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "org.example")

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.assertCompletionVariants(maven.fixture, maven.RENDERING_TEXT, "1.0", "2.0")

    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencyManagement>
                                             <dependencies>
                                                 <dependency>
                                                     <groupId>org.example</groupId>
                                                     <artifactId>intellijmaventest</artifactId>
                                                     <version>2.0</version>
                                                 </dependency>
                                             </dependencies>
                                         </dependencyManagement>
                                         """.trimIndent()))
  }

  @Test
  fun testCompletionArtifactIdWithManagedDependency() = runBlocking {

    maven.createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                         <dependencyManagement>
                           <dependencies>
                             <dependency>
                               <groupId>org.intellijgroup</groupId>
                               <artifactId>intellijartifactanother</artifactId>
                               <version>1.0</version>
                             </dependency>
                           </dependencies>
                         </dependencyManagement>
                       <dependencies>
                         <dependency>
                           <artifactId>intellijartifactan<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.refreshFiles(listOf(maven.projectPom))
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)

    var elements = maven.fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements!!)
    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    elements = maven.fixture.completeBasic()
    UsefulTestCase.assertSize(1, elements)
    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                           <dependencyManagement>
                                             <dependencies>
                                               <dependency>
                                                 <groupId>org.intellijgroup</groupId>
                                                 <artifactId>intellijartifactanother</artifactId>
                                                 <version>1.0</version>
                                               </dependency>
                                             </dependencies>
                                           </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>org.intellijgroup</groupId>
                                               <artifactId>intellijartifactanother</artifactId>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()
    ))
  }

  @Test
  fun testCompletionGroupIdWithManagedDependencyWithTypeAndClassifier() = runBlocking {

    maven.createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>commons-io</groupId>
                             <artifactId>commons-io</artifactId>
                             <classifier>${'$'}{ioClassifier}</classifier>
                             <type>${'$'}{ioType}</type>
                             <version>2.4</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>
                             <groupId>commons-i<caret></groupId>
                             <artifactId>commons-io</artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.refreshFiles(listOf(maven.projectPom))
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)

    val elements = maven.fixture.complete(CompletionType.BASIC)
    UsefulTestCase.assertSize(1, elements)
    withContext(Dispatchers.EDT) {
      maven.fixture.finishLookup('\n')
    }

    maven.fixture.checkResult(maven.createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                               <classifier>${'$'}{ioClassifier}</classifier>
                                               <type>${'$'}{ioType}</type>
                                               <version>2.4</version>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                               <type>${'$'}{ioType}</type>
                                               <classifier>${'$'}{ioClassifier}</classifier>
                                           </dependency>
                                         </dependencies>
                                         """.trimIndent()
    ))
  }
}
