// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dsl

import com.intellij.buildsystem.model.DeclaredDependency
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenTestCase
import org.junit.Test

class MavenDependencyModificatorTest : MavenTestCase() {

  @Test
  fun testShouldReturnDependencyDirectlyDeclared() {
    val dep = MavenDependencyModificator(myProject)
    val file = createProjectPom("""
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
    val dependencyList = dep.declaredDependencies(file)
    assertDependencies(dependencyList, "org.test:test-dep:2.0")
  }

  @Test
  fun testShouldReturnDependencyManagedInParent() {
    val dep = MavenDependencyModificator(myProject)
    createProjectPom("""
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

    val moduleFile = createModulePom("m1", """
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