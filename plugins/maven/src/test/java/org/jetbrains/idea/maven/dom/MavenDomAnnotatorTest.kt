// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.junit.Test

class MavenDomAnnotatorTest : MavenDomTestCase() {

  @Test
  fun testAnnotatePlugin() {
    val modulePom = createModulePom("m", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>

<artifactId>m</artifactId>
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
    </plugin>
  </plugins>
</build>
""")

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m</module>
</modules>

<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
""")

    checkGutters(modulePom, listOf(
      "<artifactId>maven-compiler-plugin</artifactId>",
      """<parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
         </parent>"""))
  }

  @Test
  fun testAnnotateDependency() {
    val modulePom = createModulePom("m", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
    
<artifactId>m</artifactId>
<dependencies>
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>                
  </dependency>
</dependencies>
""")

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m</module>
</modules>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>                
      <version>4.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
""")

    checkGutters(modulePom, listOf(
      """<dependency>
         <groupId>junit</groupId>
         <artifactId>junit</artifactId>       
         </dependency>""",
      """<parent>
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         </parent>"""))
  }

  @Test
  fun testAnnotateDependencyWithEmptyRelativePath() {
    val modulePom = createModulePom("m", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <relativePath/>
</parent>
    
<artifactId>m</artifactId>
<dependencies>
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>                
  </dependency>
</dependencies>
""")

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m</module>
</modules>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>                
      <version>4.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
""")

    checkGutters(modulePom, listOf(
      """<dependency>
         <groupId>junit</groupId>
         <artifactId>junit</artifactId>       
         </dependency>""",
      """<parent>
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         <relativePath/>
         </parent>"""))
  }

  private fun checkGutters(virtualFile: VirtualFile, expectedProperties: Collection<String>) {
    val file = PsiManager.getInstance(myProject).findFile(virtualFile)!!
    myFixture.configureFromExistingVirtualFile(virtualFile)

    val text = file.text
    val actualProperties = myFixture.doHighlighting()
      .filter { it.gutterIconRenderer != null }
      .map { text.substring(it.getStartOffset(), it.getEndOffset()) }
      .map { it.replace(" ", "") }
      .toSet()

    val expectedPropertiesClearing = expectedProperties
      .map { it.replace(" ", "") }
      .toSet()
    assertEquals(expectedPropertiesClearing, actualProperties)
  }
}