// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.properties.IProperty
import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem
import org.junit.Test

class MavenPropertyCompletionAndResolutionTest : MavenDomTestCase() {
  
  override fun setUp() = runBlocking {
    super.setUp()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testResolutionToProject() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToProjectAt() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>@<caret>project.version@</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testCorrectlyCalculatingTextRangeWithLeadingWhitespaces() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>     ${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testBuiltInBasedirProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>basedir}</name>
                       """.trimIndent())

    val baseDir = readAction { PsiManager.getInstance(project).findDirectory(projectPom.getParent())!! }
    assertResolved(projectPom, baseDir)

    updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.basedir}</name>
                       """.trimIndent())


    assertResolved(projectPom, baseDir)

    updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.basedir}</name>
                       """.trimIndent())

    assertResolved(projectPom, baseDir)
  }

  @Test
  fun testBuiltInMavenMultimoduleDirProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <myDir>${'$'}{<caret>maven.multiModuleProjectDirectory}</myDir>
                       </properties>>
                       """.trimIndent())

    val multimoduleDir = readAction { PsiManager.getInstance(project).findDirectory(projectPom.getParent()) }
    assertResolved(projectPom, multimoduleDir!!)
  }

  @Test
  fun testBuiltInMavenMultimoduleDirPropertyParentFile() = runBlocking {
    createModulePom("m1",
                    """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())
    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    updateAllProjects()

    val m1 = updateModulePom("m1",
                             """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       <properties>
                                         <myDir>${'$'}{<caret>maven.multiModuleProjectDirectory}</myDir>
                                       </properties>
                                       """.trimIndent())
    val multimoduleDir = readAction { PsiManager.getInstance(project).findDirectory(projectPom.getParent()) }
    assertResolved(m1, multimoduleDir!!)
  }

  @Test
  fun testResolutionWithSeveralProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.artifactId}-${'$'}{project.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.artifactId"))

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.artifactId}-${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testResolvingFromPropertiesSection() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>${'$'}{<caret>project.version}</foo>
                       </properties>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testResolvingFromPropertiesSectionAt() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>@<caret>project.version@</foo>
                       </properties>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToUnknownProjectProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.bar}</name>
                       """.trimIndent())

    assertUnresolved(projectPom)
  }

  @Test
  fun testResolutionToAbsentProjectProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.description}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.name"))
  }

  @Test
  fun testResolutionToAbsentPomProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.description}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.name"))
  }

  @Test
  fun testResolutionToAbsentUnclassifiedProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>description}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.name"))
  }

  @Test
  fun testResolutionToPomProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToUnclassifiedProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToDerivedCoordinatesFromProjectParent() = runBlocking {
    updateProjectPom("""
                       <artifactId>project</artifactId>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.parent.version"))
  }

  @Test
  fun testResolutionToProjectParent() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.parent.version}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag("project.parent.version"))
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForManagedParent() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <build>
                        <directory>dir</directory>
                       </build>
                       """.trimIndent())

    val child = createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>project</artifactId>
                                            <version>1</version>
                                          </parent>
                                          <name>${'$'}{project.build.directory}</name>
                                          """.trimIndent())
    importProjectsAsync(projectPom, child)

    createModulePom("child",
                    """
                      <groupId>test</groupId>
                      <artifactId>child</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <name>${'$'}{<caret>project.build.directory}</name>
                      """.trimIndent())

    assertResolved(child, findTag(projectPom, "project.build.directory"))
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForRelativeParent() = runBlocking {
    updateProjectPom("""
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <parent>
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <relativePath>./parent/pom.xml</version>
                     </parent>
                     <name>${'$'}{<caret>project.build.directory}</name>
                     """.trimIndent())

    val parent = createModulePom("parent",
                                 """
                                         <groupId>test</groupId>
                                         <artifactId>parent</artifactId>
                                         <version>1</version>
                                         <build>
                                           <directory>dir</directory>
                                         </build>
                                         """.trimIndent())

    assertResolved(projectPom, findTag(parent, "project.build.directory"))
  }

  @Test
  fun testResolutionToInheritedPropertiesForNonManagedParent() = runBlocking {
    updateProjectPom("""
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <parent>
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <relativePath>parent/pom.xml</version>
                     </parent>
                     <name>${'$'}{<caret>foo}</name>
                     """.trimIndent())

    val parent = createModulePom("parent",
                                 """
                                         <groupId>test</groupId>
                                         <artifactId>parent</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <foo>value</foo>
                                         </properties>
                                         """.trimIndent())

    assertResolved(projectPom, findTag(parent, "project.properties.foo"))
  }

  @Test
  fun testResolutionToInheritedSuperPomProjectProperty() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.build.finalName}</name>
                       """.trimIndent())

    val effectiveSuperPom = MavenUtil.resolveSuperPomFile(project, projectPom)
    assertNotNull(effectiveSuperPom)
    assertResolved(projectPom, findTag(effectiveSuperPom!!, "project.build.finalName"))
  }

  @Test
  fun testHandleResolutionRecursion() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>./parent/pom.xml</version>
                       </parent>
                       <name>${'$'}{<caret>project.description}</name>
                       """.trimIndent())

    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <relativePath>../pom.xml</version>
                      </parent>
                      """.trimIndent())

    assertResolved(projectPom, findTag(projectPom, "project.name"))
  }

  @Test
  fun testResolutionFromProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag(projectPom, "project.properties.foo"))
  }

  @Test
  fun testResolutionWithProfiles() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                       </profiles>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    assertResolved(projectPom, findTag(projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionToPropertyDefinedWithinProfiles() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                           <build>
                             <finalName>${'$'}{<caret>foo}</finalName>
                           </build>
                         </profile>
                       </profiles>
                       """.trimIndent())

    readWithProfiles("one")
    assertResolved(projectPom, findTag(projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionToPropertyDefinedOutsideProfiles() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <build>
                             <finalName>${'$'}{<caret>foo}</finalName>
                           </build>
                         </profile>
                       </profiles>
                       """.trimIndent())

    readWithProfiles("one")
    assertResolved(projectPom, findTag(projectPom, "project.properties.foo"))
  }

  @Test
  fun testResolutionWithDefaultProfiles() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                       </profiles>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    updateAllProjects()

    assertResolved(projectPom, findTag(projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionWithTriggeredProfiles() = runBlocking {
    needFixForMaven4()
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                       </profiles>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    updateAllProjects()

    assertResolved(projectPom, findTag(projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolvingToProfilesBeforeModelsProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>value</foo>
                           </properties>
                         </profile>
                       </profiles>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("one")

    assertResolved(projectPom, findTag(projectPom, "project.profiles[0].properties.foo"))
  }

  @Test
  fun testResolvingPropertiesInSettingsXml() = runBlocking {
    // we are changing settings.xml here, and we need a new maven embedder--
    // the old one (that was created during the first sync in setUp()) doesn't know about new settings.xml,
    // so it won't be able to find the profiles
    MavenServerManager.getInstance().closeAllConnectorsAndWait()

    val profiles = updateSettingsXml("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <foo>value one</foo>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <foo>value two</foo>
                           </properties>
                         </profile>
                       </profiles>
                       """.trimIndent())

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    moveCaretTo(projectPom, "<name>${'$'}{<caret>foo}</name>")
    assertResolved(projectPom, findTag(profiles, "settings.profiles[1].properties.foo", MavenDomSettingsModel::class.java))
  }

  @Test
  fun testResolvingSettingsModelProperties() = runBlocking {
    val profiles = updateSettingsXml("""
  <localRepository>
  ${repositoryPath}</localRepository>
  """.trimIndent())

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag(profiles, "settings.localRepository", MavenDomSettingsModel::class.java))
  }

  @Test
  fun testCompletionPropertyInsideSettingsXml() = runBlocking {
    val profiles = updateSettingsXml("""
                                               <profiles>
                                                 <profile>
                                                   <id>one</id>
                                                   <properties>
                                                     <foo>value</foo>
                                                     <bar>value</bar>
                                                     <xxx>${'$'}{<caret>}</xxx>
                                                   </properties>
                                                 </profile>
                                               </profiles>
                                               """.trimIndent())

    refreshFiles(listOf(profiles))
    fixture.configureFromExistingVirtualFile(profiles)

    fixture.complete(CompletionType.BASIC)
    val strings = fixture.getLookupElementStrings()!!

    assert(strings.containsAll(mutableListOf("foo", "bar")))
    assert(!strings.contains("xxx"))
  }

  @Test
  fun testResolvePropertyInsideSettingsXml() = runBlocking {
    val profiles = updateSettingsXml("""
                                               <profiles>
                                                 <profile>
                                                   <id>one</id>
                                                   <properties>
                                                     <foo>value</foo>
                                                     <bar>${'$'}{<caret>foo}</bar>
                                                   </properties>
                                                 </profile>
                                               </profiles>
                                               """.trimIndent())

    refreshFiles(listOf(profiles))
    fixture.configureFromExistingVirtualFile(profiles)

    readAction {
      val elementAtCaret = fixture.getElementAtCaret()
      assert(elementAtCaret is XmlTag)
      assertEquals("foo", (elementAtCaret as XmlTag).getName())
    }
  }

  @Test
  fun testResolvingAbsentSettingsModelProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    assertResolved(projectPom, findTag(projectPom, "project.name"))
  }

  @Test
  fun testResolvingUnknownSettingsModelProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.foo.bar}</name>
                       """.trimIndent())

    assertUnresolved(projectPom)
  }

  @Test
  fun testResolvingPropertiesInOldStyleProfilesXml() = runBlocking {
    val profiles = createProfilesXmlOldStyle("""
                                                       <profile>
                                                         <id>one</id>
                                                         <properties>
                                                           <foo>value</foo>
                                                         </properties>
                                                       </profile>
                                                       <profile>
                                                         <id>two</id>
                                                         <properties>
                                                           <foo>value</foo>
                                                         </properties>
                                                       </profile>
                                                       """.trimIndent())

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    assertResolved(projectPom, findTag(profiles, "profiles[1].properties.foo", MavenDomProfiles::class.java))
  }

  @Test
  fun testResolvingInheritedProperties() = runBlocking {
    updateProjectPom("""
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <parent>
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <relativePath>./parent/pom.xml</version>
                     </parent>
                     <name>${'$'}{<caret>foo}</name>
                     """.trimIndent())

    val parent = createModulePom("parent",
                                 """
                                         <groupId>test</groupId>
                                         <artifactId>parent</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <foo>value</foo>
                                         </properties>
                                         """.trimIndent())
    assertResolved(projectPom, findTag(parent, "project.properties.foo"))
  }

  @Test
  fun testSystemProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>user.home}</name>
                       """.trimIndent())

    val psiElement = readAction { MavenPropertiesVirtualFileSystem.getInstance().findSystemProperty(project, "user.home")!!.getPsiElement() }
    assertResolved(projectPom, psiElement)
  }

  @Test
  fun testEnvProperties() = runBlocking {
    updateProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <name>${"$"}{<caret>env.${envVar}}</name>
  """.trimIndent())

    val psiElement = readAction { MavenPropertiesVirtualFileSystem.getInstance().findEnvProperty(project, envVar)!!.getPsiElement() }
    assertResolved(projectPom, psiElement)
  }

  @Test
  fun testUpperCaseEnvPropertiesOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PATH}</name>
                       """.trimIndent())

    val ref = getReferenceAtCaret(projectPom)
    assertNotNull(ref)

    readAction {
      val resolved = ref!!.resolve()
      assertEquals(System.getenv("Path").replace("[^A-Za-z]".toRegex(), ""),
                   (resolved as IProperty?)!!.getValue()!!.replace("[^A-Za-z]".toRegex(), ""))
    }
  }

  @Test
  fun testCaseInsencitiveOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PaTH}</name>
                       """.trimIndent())

    assertUnresolved(projectPom)
  }

  @Test
  fun testNotUpperCaseEnvPropertiesOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.Path}</name>
                       """.trimIndent())

    assertUnresolved(projectPom)
  }


  @Test
  fun testHighlightUnresolvedProperties() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>child</artifactId>
                       <version>1</version>
                       <name>${'$'}{xxx}</name>
                       <properties>
                         <foo>
                       ${'$'}{zzz}
                       ${'$'}{pom.maven.build.timestamp}
                       ${'$'}{project.maven.build.timestamp}
                       ${'$'}{parent.maven.build.timestamp}
                       ${'$'}{baseUri}
                       ${'$'}{unknownProperty}
                       ${'$'}{project.version.bar}
                       ${'$'}{maven.build.timestamp}
                       ${'$'}{project.parentFile.name}
                       ${'$'}{project.parentFile.nameXxx}
                       ${'$'}{pom.compileArtifacts.empty}
                       ${'$'}{modules.empty}
                       ${'$'}{projectDirectory}
                       </foo>
                       </properties>
                       """.trimIndent()
    )

    checkHighlighting(projectPom,
                      Highlight(text = "xxx"),
                      Highlight(text = "zzz"),
                      Highlight(text = "pom.maven.build.timestamp"),
                      Highlight(text = "parent.maven.build.timestamp"),
                      Highlight(text = "baseUri"),
                      Highlight(text = "unknownProperty"),
                      Highlight(text = "project.version.bar"),
                      Highlight(text = "project.parentFile.nameXxx"),
    )

  }

  @Test
  fun testCompletion() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>./parent/pom.xml</version>
                       </parent>
                       <properties>
                         <pomProp>value</pomProp>
                       </properties>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <pomProfilesProp>value</pomProfilesProp>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <pomProfilesPropInactive>value</pomProfilesPropInactive>
                           </properties>
                         </profile>
                       </profiles>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())

    createProfilesXml("""
                        <profile>
                          <id>one</id>
                          <properties>
                            <profilesXmlProp>value</profilesXmlProp>
                          </properties>
                        </profile>
                        """.trimIndent())

    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <parentPomProp>value</parentPomProp>
                      </properties>
                      <profiles>
                        <profile>
                          <id>one</id>
                          <properties>
                            <parentPomProfilesProp>value</parentPomProfilesProp>
                          </properties>
                        </profile>
                      </profiles>
                      """.trimIndent())

    createProfilesXml("parent",
                      """
                        <profile>
                          <id>one</id>
                          <properties>
                            <parentProfilesXmlProp>value</parentProfilesXmlProp>
                          </properties>
                        </profile>
                        """.trimIndent())

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <properties>
                              <settingsXmlProp>value</settingsXmlProp>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())

    readWithProfiles("one")

    val variants = getCompletionVariants(projectPom)
    assertContain(variants, "pomProp", "pomProfilesProp", "profilesXmlProp")
    assertContain(variants,
                  "parentPomProp",
                  "parentPomProfilesProp",
                  "parentProfilesXmlProp")
    assertContain(variants, "artifactId", "project.artifactId", "pom.artifactId")
    assertContain(variants, "basedir", "project.basedir", "pom.basedir", "project.baseUri", "pom.basedir")
    assertDoNotContain(variants, "baseUri")
    assertContain(variants, "maven.build.timestamp")
    assertContain(variants, "maven.multiModuleProjectDirectory")
    assertDoNotContain(variants, "project.maven.build.timestamp")
    assertContain(variants, "settingsXmlProp")
    assertContain(variants, "settings.localRepository")
    assertContain(variants, "user.home", "env." + envVar)
  }

  @Test
  fun testDoNotIncludeCollectionPropertiesInCompletion() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())
    assertCompletionVariantsDoNotInclude(projectPom, "project.dependencies", "env.\\=C\\:", "idea.config.path")
  }

  @Test
  fun testCompletingAfterOpenBrace() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret></name>
                       """.trimIndent())

    assertCompletionVariantsInclude(projectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceInOpenTag() = runBlocking {
    if (ignore()) return@runBlocking

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>
                       """.trimIndent())

    assertCompletionVariantsInclude(projectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeText() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{pro<caret></name>
                       """.trimIndent())

    val variants = getCompletionVariants(projectPom)
    assertContain(variants, "project.groupId")
    assertDoNotContain(variants, "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeTextWithDot() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.g<caret></name>
                       """.trimIndent())

    val variants = getCompletionVariants(projectPom)
    assertContain(variants, "project.groupId")
    assertDoNotContain(variants, "project.name")
  }

  @Test
  fun testDoNotCompleteAfterNonWordCharacter() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<<caret>/name>
                       """.trimIndent())

    assertCompletionVariantsDoNotInclude(projectPom, "project.groupId")
  }

  private suspend fun readWithProfiles(vararg profiles: String) {
    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf(*profiles))
    updateAllProjects()
  }
}
