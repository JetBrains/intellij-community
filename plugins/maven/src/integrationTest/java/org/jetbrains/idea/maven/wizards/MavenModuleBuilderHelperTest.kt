// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModuleBuilderHelperTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun testGenerateFromArchetype() = runBlocking {
    val modulePom = maven.createModulePom("m1", """
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>"""
    )
    maven.createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <version>1</version>
    <packaging>pom</packaging>
    <modules>
        <module>m1/customName.xml</module>
    </modules>""")
    maven.importProjectAsync()

    val generatedPom = maven.createProjectSubFile("generated/m1/pom.xml",
      ("""<project
         <modelVersion>4.0.0</modelVersion>
         <groupId>test</groupId>
         <artifactId>m1</artifactId>
         <version>1</version>         
         <properties>
           <generated>generated</generated>
         </properties>
       </project>""")
    )
    val mavenProject: MavenProject? = maven.projectsManager.findProject(maven.getModule("project"))
    assertNotNull(mavenProject)

    val archetype = MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null)
    val moduleBuilderHelper = MavenModuleBuilderHelper(
      MavenId("test", "m1", "1"), mavenProject, mavenProject, true, true, archetype, emptyMap(), "test"
    )
    moduleBuilderHelper.copyGeneratedFiles(generatedPom.parent.parent.toNioPath(), modulePom, maven.project, "m1")
    val pomTxt = VfsUtil.loadText(modulePom)
    assertTrue(pomTxt.contains("parent"))
    assertTrue(pomTxt.contains("project"))
    assertTrue(pomTxt.contains("<generated>generated</generated>"))
  }
}