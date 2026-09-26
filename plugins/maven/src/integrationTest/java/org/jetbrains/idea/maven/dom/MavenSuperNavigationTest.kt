// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.configureProjectPom
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.getEditorOffset
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.performEditorAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSuperNavigationTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testNavigationToManagingDependencyWithoutModules() = runBlocking {
    maven.configureProjectPom(
      """
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
            <artifactId>junit<caret></artifactId>
          </dependency>
        </dependencies>
        """.trimIndent())

    maven.performEditorAction("GotoSuperMethod")

    checkResultWithInlays(
      maven.createPomXml(
        """
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <dependencyManagement>
            <dependencies>
              <caret><dependency>
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
            </dependency>
          </dependencies>
          """.trimIndent()))
  }

  @Test
  fun testNavigationToManagingPluginWithoutModules() = runBlocking {
    maven.configureProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <build>
          <pluginManagement>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
              </plugin>
            </plugins>
          </pluginManagement>
          <plugins>
            <plugin>
              <gro<caret>upId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
          </plugins>
        </build>
        """.trimIndent()
    )

    maven.performEditorAction("GotoSuperMethod")

    checkResultWithInlays(
      maven.createPomXml(
        """
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <build>
            <pluginManagement>
              <plugins>
                <caret><plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-compiler-plugin</artifactId>
                </plugin>
              </plugins>
            </pluginManagement>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
              </plugin>
            </plugins>
          </build>
          """.trimIndent()
      ))
  }

  @Test
  fun testGotoToParentProject() = runBlocking {
    val parent = maven.createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <modules>
          <module>m1</module>
        </modules>
        """.trimIndent())

    val m1 = maven.createModulePom(
      "m1",
      """
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version><caret>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """.trimIndent())

    maven.configTest(m1)
    maven.performEditorAction("GotoSuperMethod")

    val offset = maven.getEditorOffset(parent)
    assertEquals(0, offset)
  }

  @Test
  fun testNavigationToManagingDependencyWithModules() = runBlocking {
    val parent = maven.createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <dependencyManagement>
          <dependencies>
            <dependency>
              <groupId>junit</groupId>
              <artifactId>junit</artifactId>
              <version>4.0</version>
            </dependency>
          </dependencies>
        </dependencyManagement>
        <modules>
          <module>m1</module>
        </modules>
        """.trimIndent())

    val m1 = maven.createModulePom(
      "m1",
      """
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        <dependencies>
          <dependency>
            <groupId><caret>junit</groupId>
            <artifactId>junit<caret></artifactId>
          </dependency>
        </dependencies>
        """.trimIndent()
    )

    maven.configTest(m1)
    maven.performEditorAction("GotoSuperMethod")

    maven.configTest(parent)
    checkResultWithInlays(
      maven.createPomXml(
        """
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <packaging>pom</packaging>
          <dependencyManagement>
            <dependencies>
              <caret><dependency>
                <groupId>junit</groupId>
                <artifactId>junit</artifactId>
                <version>4.0</version>
              </dependency>
            </dependencies>
          </dependencyManagement>
          <modules>
            <module>m1</module>
          </modules>
          """.trimIndent()))
  }

  @Test
  fun testNavigationToManagingPluginWithModules() = runBlocking {
    val parent = maven.createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <modules>
          <module>m1</module>
        </modules>
        <build>
          <pluginManagement>
            <plugins>
            	 <plugin>
          		   <groupId>org.apache.maven.plugins</groupId>
          		   <artifactId>maven-compiler-plugin</artifactId>
          		   <version>3.8.1</version>
          	   </plugin>
            </plugins>
          </pluginManagement>
        </build>
        """.trimIndent()
    )

    val m1 = maven.createModulePom(
      "m1",
      """
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        <build>
          <plugins>
          	 <plugin>
        		   <groupId><caret>org.apache.maven.plugins</groupId>
        		   <artifactId>maven-compiler-plugin</artifactId>
        	   </plugin>
          </plugins>
        </build>
        """.trimIndent()
    )

    maven.configTest(m1)
    maven.performEditorAction("GotoSuperMethod")

    maven.configTest(parent)
    checkResultWithInlays(
      maven.createPomXml(
        """
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <packaging>pom</packaging>
          <modules>
            <module>m1</module>
          </modules>
          <build>
            <pluginManagement>
              <plugins>
              	 <caret><plugin>
            		   <groupId>org.apache.maven.plugins</groupId>
            		   <artifactId>maven-compiler-plugin</artifactId>
            		   <version>3.8.1</version>
            	   </plugin>
              </plugins>
            </pluginManagement>
          </build>
          """.trimIndent()
      ))
  }

  private suspend fun checkResultWithInlays(text: String) {
    withContext(Dispatchers.EDT) {
      //readaction is not enough
      writeIntentReadAction {
        maven.fixture.checkResultWithInlays(text)
      }
    }
  }
}
