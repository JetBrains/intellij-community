// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dsl

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.createModulePom
import org.jetbrains.idea.maven.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import com.intellij.testFramework.UsefulTestCase.assertSameElements

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDependencyModificatorTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun testShouldReturnDependencyDirectlyDeclared() = runBlocking {
    val dep = MavenDependencyModificator(maven.project)
    val file = maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1.0</version>
      
      <dependencies>
          <dependency>
               <groupId>org.test</groupId>
               <artifactId>test-dep</artifactId>
               <version>2.0</version>
          </dependency>
      </dependencies>
    """.trimIndent())
    maven.importProjectAsync()
    val dependencyList = dep.declaredDependencies(file)
    assertDependencies(dependencyList, "org.test:test-dep:2.0")
  }

  @Test
  fun testShouldReturnDependencyManagedInParent() = runBlocking {
    val dep = MavenDependencyModificator(maven.project)
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1.0</version>
      <modules>
          <module>m1</module>   
      </modules>
      
      <dependencyManagement>
          <dependencies>
              <dependency>
                   <groupId>org.test</groupId>
                  <artifactId>test-dep</artifactId>
                  <version>2.0</version>
              </dependency>
          </dependencies>
      </dependencyManagement>
    """.trimIndent())

    val moduleFile = maven.createModulePom("m1", """
        <parent>
          <groupId>test</groupId>
          <artifactId>test</artifactId>
          <version>1.0</version>  
        </parent>
      <artifactId>m1</artifactId>
      
       <dependencies>
          <dependency>
               <groupId>org.test</groupId>
               <artifactId>test-dep</artifactId>
          </dependency>
       </dependencies>
      
     
    """.trimIndent())
    maven.importProjectAsync()
    val dependencyList = dep.declaredDependencies(moduleFile)
    assertDependencies(dependencyList, "org.test:test-dep:2.0")
  }

  private fun assertDependencies(dependencyList: List<DeclaredDependency>?, vararg expected: String) {
    TestCase.assertNotNull(dependencyList)
    TestCase.assertEquals(expected.size, dependencyList!!.size)
    assertSameElements(dependencyList.map { "${it.coordinates.groupId}:${it.coordinates.artifactId}:${it.coordinates.version}" },
      *expected)
  }
}