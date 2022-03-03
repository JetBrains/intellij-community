// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.maven.testFramework.MavenImportingTestCase
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.Assert
import org.junit.Test

class MavenModuleBuilderHelperTest : MavenImportingTestCase() {

  @Test
  fun testGenerateFromArchetype() {
    val modulePom = createModulePom("m1", """
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>"""
    )
    createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <version>1</version>
    <packaging>pom</packaging>
    <modules>
        <module>m1/customName.xml</module>
    </modules>""")
    importProject()

    val generatedPom = createProjectSubFile("generated/m1/pom.xml",
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
    val mavenProject: MavenProject? = myProjectsManager.findProject(getModule("project"))
    assertNotNull(mavenProject)

    val archetype = MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null)
    val moduleBuilderHelper = MavenModuleBuilderHelper(
      MavenId("test", "m1", "1"), mavenProject, mavenProject, true, true, archetype, emptyMap(), "test"
    )
    moduleBuilderHelper.copyGeneratedFiles(generatedPom.parent.parent.toNioPath().toFile(), modulePom, myProject, "m1")
    val pomTxt = VfsUtil.loadText(modulePom)
    Assert.assertTrue(pomTxt.contains("parent"))
    Assert.assertTrue(pomTxt.contains("project"))
    Assert.assertTrue(pomTxt.contains("<generated>generated</generated>"))
  }
}