// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture.Highlight
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicateDependenciesInspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDuplicatedDependencyInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testDuplicatedInSameFile() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

    maven.createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <dependencies>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                           <scope>provided</scope>
                         </dependency>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDuplicatedInSameFileDifferentVersion() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

    maven.createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <dependencies>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                         </dependency>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.1</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDuplicatedInParentDifferentScope() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

    maven.createModulePom("child", """
      <groupId>mavenParent</groupId>
      <artifactId>child</artifactId>
      <version>1.0</version>
        
      <parent>
        <groupId>mavenParent</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
      </parent>
        
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>3.8.2</version>
          <scope>runtime</scope>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>parent</artifactId>
                       <version>1.0</version>
                       <packaging>pom</packaging>
                         
                       <modules>
                         <module>child</module>
                       </modules>
                         
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                           <scope>provided</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectAsync()

    maven.checkHighlighting()
  }

  @Test
  fun testDuplicatedInParentSameScope() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

    maven.createModulePom("child", """
      <groupId>mavenParent</groupId>
      <artifactId>child</artifactId>
      <version>1.0</version>
        
      <parent>
        <groupId>mavenParent</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
      </parent>
        
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>3.8.1</version>
          <scope>compile</scope>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>parent</artifactId>
                       <version>1.0</version>
                       <packaging>pom</packaging>
                         
                       <modules>
                         <module>child</module>
                       </modules>
                         
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.1</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectAsync()

    maven.checkHighlighting(maven.projectPom, Highlight(severity = HighlightSeverity.WARNING, text = "dependency", description = "Dependency is duplicated in file(s): child "))
  }

  @Test
  fun testDuplicatedInParentDifferentVersion() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

    maven.createModulePom("child", """
      <groupId>mavenParent</groupId>
      <artifactId>child</artifactId>
      <version>1.0</version>
        
      <parent>
        <groupId>mavenParent</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
      </parent>
        
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>3.8.1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>mavenParent</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                    <packaging>pom</packaging>
                      
                    <modules>
                      <module>child</module>
                    </modules>
                      
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>3.8.2</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.checkHighlighting()
  }

  @Test
  fun testDuplicatedInManagedDependencies() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

    maven.createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <dependencyManagement>
                         <dependencies>
                           <<warning>dependency</warning>>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>3.8.2</version>
                             <type>jar</type>
                           </dependency>
                         
                           <<warning>dependency</warning>>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                           </dependency>
                         
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                             <classifier>sources</classifier>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       """.trimIndent())

    maven.checkHighlighting()
  }
}
