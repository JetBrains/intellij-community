// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.properties.IProperty
import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles
import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.importing.FilesList
import org.jetbrains.idea.maven.project.importing.MavenImportFlow
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

class MavenPropertyCompletionAndResolutionTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testResolutionToProject() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToProjectAt() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>@<caret>project.version@</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testCorrectlyCalculatingTextRangeWithLeadingWhitespaces() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>     ${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testBuiltInBasedirProperty() {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>basedir}</name>
                       """.trimIndent())

    val baseDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())
    assertResolved(myProjectPom, baseDir!!)

    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.basedir}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, baseDir)

    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.basedir}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, baseDir)
  }

  @Test
  fun testBuiltInMavenMultimoduleDirProperty() {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <myDir>${'$'}{<caret>maven.multiModuleProjectDirectory}</myDir>
                       </properties>>
                       """.trimIndent())

    val multimoduleDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())
    assertResolved(myProjectPom, multimoduleDir!!)
  }

  @Test
  fun testBuiltInMavenMultimoduleDirPropertyParentFile() {
    createModulePom("m1",
                    """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())

    val m1 = createModulePom("m1",
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
    val multimoduleDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())
    assertResolved(m1, multimoduleDir!!)
  }

  @Test
  fun testResolutionWithSeveralProperties() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.artifactId}-${'$'}{project.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.artifactId"))

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.artifactId}-${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testResolvingFromPropertiesSection() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>${'$'}{<caret>project.version}</foo>
                       </properties>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testResolvingFromPropertiesSectionAt() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>@<caret>project.version@</foo>
                       </properties>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToUnknownProjectProperty() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.bar}</name>
                       """.trimIndent())

    assertUnresolved(myProjectPom)
  }

  @Test
  fun testResolutionToAbsentProjectProperty() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.description}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.name"))
  }

  @Test
  fun testResolutionToAbsentPomProperty() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.description}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.name"))
  }

  @Test
  fun testResolutionToAbsentUnclassifiedProperty() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>description}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.name"))
  }

  @Test
  fun testResolutionToPomProperty() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToUnclassifiedProperty() {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.version"))
  }

  @Test
  fun testResolutionToDerivedCoordinatesFromProjectParent() {
    createProjectPom("""
                       <artifactId>project</artifactId>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.parent.version"))
  }

  @Test
  fun testResolutionToProjectParent() {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.parent.version}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag("project.parent.version"))
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForManagedParent() {
    createProjectPom("""
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
    importProjects(myProjectPom, child)

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

    assertResolved(child, findTag(myProjectPom, "project.build.directory"))
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForRelativeParent() {
    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(parent, "project.build.directory"))
  }

  @Test
  fun testResolutionToInheritedPropertiesForNonManagedParent() {
    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(parent, "project.properties.foo"))
  }

  @Test
  fun testResolutionToInheritedSuperPomProjectProperty() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.build.finalName}</name>
                       """.trimIndent())

    val effectiveSuperPom = MavenUtil.getEffectiveSuperPom(myProject, myProjectRoot.toNioPath().toString())
    assertNotNull(effectiveSuperPom)
    assertResolved(myProjectPom, findTag(effectiveSuperPom, "project.build.finalName"))
  }

  @Test
  fun testHandleResolutionRecursion() {
    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(myProjectPom, "project.name"))
  }

  @Test
  fun testResolutionFromProperties() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"))
  }

  @Test
  fun testResolutionWithProfiles() {
    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionToPropertyDefinedWithinProfiles() {
    createProjectPom("""
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
    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionToPropertyDefinedOutsideProfiles() {
    createProjectPom("""
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
    assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"))
  }

  @Test
  fun testResolutionWithDefaultProfiles() {
    createProjectPom("""
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

    readProjects()

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionWithTriggeredProfiles() {
    createProjectPom("""
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

    readProjects()

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolvingToProfilesBeforeModelsProperties() {
    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[0].properties.foo"))
  }

  @Test
  fun testResolvingPropertiesInSettingsXml() {
    val profiles = updateSettingsXml("""
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
                                               """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    assertResolved(myProjectPom, findTag(profiles, "settings.profiles[1].properties.foo", MavenDomSettingsModel::class.java))
  }

  @Test
  fun testResolvingSettingsModelProperties() {
    val profiles = updateSettingsXml("""
  <localRepository>
  ${getRepositoryPath()}</localRepository>
  """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag(profiles, "settings.localRepository", MavenDomSettingsModel::class.java))
  }

  @Test
  fun testCompletionPropertyInsideSettingsXml() {
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

    myFixture.configureFromExistingVirtualFile(profiles)
    myFixture.complete(CompletionType.BASIC)
    val strings = myFixture.getLookupElementStrings()!!

    assert(strings.containsAll(mutableListOf("foo", "bar")))
    assert(!strings.contains("xxx"))
  }

  @Test
  fun testResolvePropertyInsideSettingsXml() {
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

    myFixture.configureFromExistingVirtualFile(profiles)
    val elementAtCaret = myFixture.getElementAtCaret()
    assert(elementAtCaret is XmlTag)
    assertEquals("foo", (elementAtCaret as XmlTag).getName())
  }

  @Test
  fun testResolvingAbsentSettingsModelProperties() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    assertResolved(myProjectPom, findTag(myProjectPom, "project.name"))
  }

  @Test
  fun testResolvingUnknownSettingsModelProperties() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.foo.bar}</name>
                       """.trimIndent())

    assertUnresolved(myProjectPom)
  }

  @Test
  fun testResolvingPropertiesInProfilesXml() {
    val profiles = createProfilesXml("""
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

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    assertResolved(myProjectPom, findTag(profiles, "profilesXml.profiles[1].properties.foo", MavenDomProfilesModel::class.java))
  }

  @Test
  fun testResolvingPropertiesInOldStyleProfilesXml() {
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

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    assertResolved(myProjectPom, findTag(profiles, "profiles[1].properties.foo", MavenDomProfiles::class.java))
  }

  @Test
  fun testResolvingInheritedProperties() {
    createProjectPom("""
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

    assertResolved(myProjectPom, findTag(parent, "project.properties.foo"))
  }

  @Test
  fun testSystemProperties() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>user.home}</name>
                       """.trimIndent())

    assertResolved(myProjectPom,
                   MavenPropertiesVirtualFileSystem.getInstance().findSystemProperty(myProject, "user.home")!!.getPsiElement())
  }

  @Test
  fun testEnvProperties() {
    createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <name>${"$"}{<caret>env.${getEnvVar()}}</name>
  """.trimIndent())

    assertResolved(myProjectPom, MavenPropertiesVirtualFileSystem.getInstance().findEnvProperty(myProject, getEnvVar())!!.getPsiElement())
  }

  @Test
  fun testUpperCaseEnvPropertiesOnWindows() {
    if (!SystemInfo.isWindows) return

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PATH}</name>
                       """.trimIndent())

    val ref = getReferenceAtCaret(myProjectPom)
    assertNotNull(ref)

    val resolved = ref.resolve()
    assertEquals(System.getenv("Path").replace("[^A-Za-z]".toRegex(), ""),
                 (resolved as IProperty?)!!.getValue()!!.replace("[^A-Za-z]".toRegex(), ""))
  }

  @Test
  fun testCaseInsencitiveOnWindows() {
    if (!SystemInfo.isWindows) return

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PaTH}</name>
                       """.trimIndent())

    assertUnresolved(myProjectPom)
  }

  @Test
  fun testNotUpperCaseEnvPropertiesOnWindows() {
    if (!SystemInfo.isWindows) return

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.Path}</name>
                       """.trimIndent())

    assertUnresolved(myProjectPom)
  }

  @Test
  fun testHighlightUnresolvedProperties() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>child</artifactId>
                       <version>1</version>
                       <name>${'$'}{<error>xxx</error>}</name>
                       <properties>
                         <foo>
                       ${'$'}{<error>zzz</error>}
                       ${'$'}{<error>pom.maven.build.timestamp</error>}
                       ${'$'}{<error>project.maven.build.timestamp</error>}
                       ${'$'}{<error>parent.maven.build.timestamp</error>}
                       ${'$'}{<error>baseUri</error>}
                       ${'$'}{<error>unknownProperty</error>}
                       ${'$'}{<error>project.version.bar</error>}
                       ${'$'}{maven.build.timestamp}
                       ${'$'}{project.parentFile.name}
                       ${'$'}{<error>project.parentFile.nameXxx</error>}
                       ${'$'}{pom.compileArtifacts.empty}
                       ${'$'}{modules.empty}
                       ${'$'}{projectDirectory}
                       </foo>
                       </properties>
                       """.trimIndent()
    )

    checkHighlighting()
  }

  @Test
  fun testCompletion() {
    createProjectPom("""
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

    val variants = getCompletionVariants(myProjectPom)
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
    assertContain(variants, "user.home", "env." + getEnvVar())
  }

  @Test
  fun testDoNotIncludeCollectionPropertiesInCompletion() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())
    assertCompletionVariantsDoNotInclude(myProjectPom, "project.dependencies", "env.\\=C\\:", "idea.config.path")
  }

  @Test
  fun testCompletingAfterOpenBrace() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret></name>
                       """.trimIndent())

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceInOpenTag() {
    if (ignore()) return

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>
                       """.trimIndent())

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeText() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{pro<caret></name>
                       """.trimIndent())

    val variants = getCompletionVariants(myProjectPom)
    assertContain(variants, "project.groupId")
    assertDoNotContain(variants, "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeTextWithDot() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.g<caret></name>
                       """.trimIndent())

    val variants = getCompletionVariants(myProjectPom)
    assertContain(variants, "project.groupId")
    assertDoNotContain(variants, "project.name")
  }

  @Test
  fun testDoNotCompleteAfterNonWordCharacter() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<<caret>/name>
                       """.trimIndent())

    assertCompletionVariantsDoNotInclude(myProjectPom, "project.groupId")
  }

  private fun readWithProfiles(vararg profiles: String) {
    if (isNewImportingProcess) {
      readWithProfilesViaImportFlow(*profiles)
    }
    else {
      projectsManager.explicitProfiles = MavenExplicitProfiles(Arrays.asList(*profiles))
      projectsManager.scheduleUpdateAll(MavenImportSpec(false, false, false))
      waitForReadingCompletion()
    }
  }

  override fun readProjects() {
    readWithProfiles()
  }

  private fun readWithProfilesViaImportFlow(vararg profiles: String) {
    val flow = MavenImportFlow()
    val initialImportContext =
      flow.prepareNewImport(myProject,
                            FilesList(myAllPoms),
                            mavenGeneralSettings,
                            mavenImporterSettings,
                            Arrays.asList(*profiles),
                            emptyList())
    projectsManager.initForTests()
    ApplicationManager.getApplication().executeOnPooledThread {
      myReadContext = flow.readMavenFiles(initialImportContext, mavenProgressIndicator)
      projectsManager.setProjectsTree(myReadContext!!.projectsTree)
    }[10, TimeUnit.SECONDS]
  }
}
