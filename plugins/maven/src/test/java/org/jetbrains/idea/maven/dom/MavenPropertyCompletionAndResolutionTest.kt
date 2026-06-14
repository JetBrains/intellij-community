// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.properties.IProperty
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture.Highlight
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertDoNotContain
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProfilesXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.envVar
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.moveCaretTo
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsDoNotInclude
import org.jetbrains.idea.maven.fixtures.assertCompletionVariantsInclude
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.assertUnresolved
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.fixtures.getCompletionVariants
import org.jetbrains.idea.maven.fixtures.getReferenceAtCaret
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPropertyCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("""
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        """.trimIndent())
  }

  @Test
  fun testResolutionToProject() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testResolutionToProjectAt() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>@<caret>project.version@</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testCorrectlyCalculatingTextRangeWithLeadingWhitespaces() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>     ${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testBuiltInBasedirProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>basedir}</name>
                       """.trimIndent())

    val baseDir = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent())!! }
    maven.assertResolved(maven.projectPom, baseDir)

    maven.updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.basedir}</name>
                       """.trimIndent())


    maven.assertResolved(maven.projectPom, baseDir)

    maven.updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.basedir}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, baseDir)
  }

  @Test
  fun testBuiltInMavenMultimoduleDirProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <myDir>${'$'}{<caret>maven.multiModuleProjectDirectory}</myDir>
                       </properties>>
                       """.trimIndent())

    val multimoduleDir = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent()) }
    maven.assertResolved(maven.projectPom, multimoduleDir!!)
  }

  @Test
  fun testBuiltInMavenMultimoduleDirPropertyParentFile() = runBlocking {
    maven.createModulePom("m1",
                    """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    maven.updateAllProjects()

    val m1 = maven.updateModulePom("m1",
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
    val multimoduleDir = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent()) }
    maven.assertResolved(m1, multimoduleDir!!)
  }

  @Test
  fun testResolutionWithSeveralProperties() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.artifactId}-${'$'}{project.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.artifactId"))

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.artifactId}-${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testResolvingFromPropertiesSection() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>${'$'}{<caret>project.version}</foo>
                       </properties>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testResolvingFromPropertiesSectionAt() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>@<caret>project.version@</foo>
                       </properties>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testResolutionToUnknownProjectProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.bar}</name>
                       """.trimIndent())

    maven.assertUnresolved(maven.projectPom)
  }

  @Test
  fun testResolutionToAbsentProjectProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.description}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.name"))
  }

  @Test
  fun testResolutionToAbsentPomProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.description}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.name"))
  }

  @Test
  fun testResolutionToAbsentUnclassifiedProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>description}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.name"))
  }

  @Test
  fun testResolutionToPomProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testResolutionToUnclassifiedProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.version"))
  }

  @Test
  fun testResolutionToDerivedCoordinatesFromProjectParent() = runBlocking {
    maven.updateProjectPom("""
                       <artifactId>project</artifactId>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.parent.version"))
  }

  @Test
  fun testResolutionToProjectParent() = runBlocking {
    maven.updateProjectPom($$"""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${<caret>project.parent.version}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag("project.parent.version"))
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForManagedParent() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <build>
                        <directory>dir</directory>
                       </build>
                       """.trimIndent())

    val child = maven.createModulePom("child",
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
    maven.importProjectsAsync(maven.projectPom, child)

    maven.createModulePom("child",
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

    maven.assertResolved(child, maven.findTag(maven.projectPom, "project.build.directory"))
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForRelativeParent() = runBlocking {
    maven.updateProjectPom("""
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

    val parent = maven.createModulePom("parent",
                                 """
                                         <groupId>test</groupId>
                                         <artifactId>parent</artifactId>
                                         <version>1</version>
                                         <build>
                                           <directory>dir</directory>
                                         </build>
                                         """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(parent, "project.build.directory"))
  }

  @Test
  fun testResolutionToInheritedPropertiesForNonManagedParent() = runBlocking {
    maven.updateProjectPom("""
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

    val parent = maven.createModulePom("parent",
                                 """
                                         <groupId>test</groupId>
                                         <artifactId>parent</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <foo>value</foo>
                                         </properties>
                                         """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(parent, "project.properties.foo"))
  }

  @Test
  fun testResolutionToInheritedSuperPomProjectProperty() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.build.finalName}</name>
                       """.trimIndent())

    val effectiveSuperPom = MavenUtil.resolveSuperPomFile(maven.project, maven.projectPom)
    assertNotNull(effectiveSuperPom)
    maven.assertResolved(maven.projectPom, maven.findTag(effectiveSuperPom!!, "project.build.finalName"))
  }

  @Test
  fun testHandleResolutionRecursion() = runBlocking {
    maven.updateProjectPom("""
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

    maven.createModulePom("parent",
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

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.name"))
  }

  @Test
  fun testResolutionFromProperties() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.properties.foo"))
  }

  @Test
  fun testResolutionWithProfiles() = runBlocking {
    maven.updateProjectPom("""
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
                       <name>${'$'}{foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    maven.moveCaretTo(maven.projectPom, """
      </profiles>
      <name>${'$'}{<caret>foo}</name>""".trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionToPropertyDefinedWithinProfiles() = runBlocking {
    maven.updateProjectPom("""
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
    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionToPropertyDefinedOutsideProfiles() = runBlocking {
    maven.updateProjectPom("""
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
    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.properties.foo"))
  }

  @Test
  fun testResolutionWithDefaultProfiles() = runBlocking {
    maven.updateProjectPom("""
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
                       <name>${'$'}{foo}</name>
                       """.trimIndent())

    maven.updateAllProjects()

    maven.moveCaretTo(maven.projectPom, """
      </profiles>
      <name>${'$'}{<caret>foo}</name>""".trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolutionWithTriggeredProfiles() = runBlocking {
    maven.importProjectAsync("""
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
                       <name>${'$'}{foo}</name>
                       """.trimIndent())

    maven.createProjectPom("""
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
""")

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.profiles[1].properties.foo"))
  }

  @Test
  fun testResolvingToProfilesBeforeModelsProperties() = runBlocking {
    maven.updateProjectPom("""
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

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.profiles[0].properties.foo"))
  }

  @Test
  fun testResolvingPropertiesInSettingsXml() = runBlocking {
    // we are changing settings.xml here, and we need a new maven embedder--
    // the old one (that was created during the first sync in setUp()) doesn't know about new settings.xml,
    // so it won't be able to find the profiles
    MavenServerManager.getInstance().closeAllConnectorsAndWait()

    val profiles = maven.updateSettingsXml("""
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

    maven.updateProjectPom($$"""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    val tag = maven.findTag(profiles, "settings.profiles[1].properties.foo", MavenDomSettingsModel::class.java)
    maven.moveCaretTo(maven.projectPom, $$"<name>${<caret>foo}</name>")
    maven.assertResolved(maven.projectPom, tag)
  }

  @Test
  fun testResolvingSettingsModelProperties() = runBlocking {
    val profiles = maven.updateSettingsXml("""
  <localRepository>
  ${maven.repositoryPath}</localRepository>
  """.trimIndent())

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(profiles, "settings.localRepository", MavenDomSettingsModel::class.java))
  }

  @Test
  fun testCompletionPropertyInsideSettingsXml() = runBlocking {
    val profiles = maven.updateSettingsXml("""
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

    maven.refreshFiles(listOf(profiles))
    maven.fixture.configureFromExistingVirtualFile(profiles)

    maven.fixture.complete(CompletionType.BASIC)
    val strings = maven.fixture.getLookupElementStrings()!!

    assert(strings.containsAll(mutableListOf("foo", "bar")))
    assert(!strings.contains("xxx"))
  }

  @Test
  fun testResolvePropertyInsideSettingsXml() = runBlocking {
    val profiles = maven.updateSettingsXml("""
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

    maven.refreshFiles(listOf(profiles))
    maven.fixture.configureFromExistingVirtualFile(profiles)

    readAction {
      val elementAtCaret = maven.fixture.getElementAtCaret()
      assert(elementAtCaret is XmlTag)
      assertEquals("foo", (elementAtCaret as XmlTag).getName())
    }
  }

  @Test
  fun testResolvingAbsentSettingsModelProperties() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.name"))
  }

  @Test
  fun testResolvingUnknownSettingsModelProperties() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.foo.bar}</name>
                       """.trimIndent())

    maven.assertUnresolved(maven.projectPom)
  }

  @Test
  fun testResolvingInheritedProperties() = runBlocking {
    maven.updateProjectPom("""
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

    val parent = maven.createModulePom("parent",
                                 """
                                         <groupId>test</groupId>
                                         <artifactId>parent</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <foo>value</foo>
                                         </properties>
                                         """.trimIndent())
    maven.assertResolved(maven.projectPom, maven.findTag(parent, "project.properties.foo"))
  }

  @Test
  fun testSystemProperties() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>user.home}</name>
                       """.trimIndent())

    val psiElement = readAction { MavenPropertiesVirtualFileSystem.getInstance().findSystemProperty(maven.project, "user.home")!!.getPsiElement() }
    maven.assertResolved(maven.projectPom, psiElement)
  }

  @Test
  fun testEnvProperties() = runBlocking {
    maven.updateProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <name>${"$"}{<caret>env.${maven.envVar}}</name>
  """.trimIndent())

    val psiElement = readAction { MavenPropertiesVirtualFileSystem.getInstance().findEnvProperty(maven.project, maven.envVar)!!.getPsiElement() }
    maven.assertResolved(maven.projectPom, psiElement)
  }

  @Test
  fun testUpperCaseEnvPropertiesOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PATH}</name>
                       """.trimIndent())

    val ref = maven.getReferenceAtCaret(maven.projectPom)
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

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PaTH}</name>
                       """.trimIndent())

    maven.assertUnresolved(maven.projectPom)
  }

  @Test
  fun testResolvingPropertiesToThemselves() = runBlocking {
    MavenPropertyPsiReference.PROPS_RESOLVING_TO_MY_ELEMENT.forEach { propertyName ->
      maven.updateProjectPom("""
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                         <name>${'$'}{<caret>$propertyName}</name>
                         """.trimIndent())
      val ref = maven.getReferenceAtCaret(maven.projectPom)!!
      maven.assertResolved(maven.projectPom, ref.element)
    }
  }

  @Test
  fun testParsedVersionResolving() = runBlocking {
    maven.updateProjectPom("""
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <name>${'$'}{<caret>parsedVersion.majorVersion}</name>
                        <build>
                          <plugins>
                            <plugin>
                              <groupId>org.codehaus.mojo</groupId>
                              <artifactId>build-helper-maven-plugin</artifactId>
                              <executions>
                                <execution>
                                  <goals>
                                    <goal>parse-version</goal>
                                  </goals>
                                </execution>
                              </executions>
                            </plugin>
                          </plugins>
                        </build>
                    """.trimIndent())
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)
    // Resolving this property depends on the presence of the build-helper-maven-plugin in pom. Reimport to add the plugin in MavenProject.
    runBlocking { maven.importProjectAsync() }
    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.version"))
  }

  @Test
  fun testParsedVersionResolvingCustomPrefix() = runBlocking {
    maven.updateProjectPom("""
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        <name>${'$'}{<caret>parsedVersionCustom.majorVersion}</name>
                        <build>
                          <plugins>
                            <plugin>
                              <groupId>org.codehaus.mojo</groupId>
                              <artifactId>build-helper-maven-plugin</artifactId>
                              <executions>
                                <execution>
                                  <goals>
                                    <goal>parse-version</goal>
                                  </goals>
                                </execution>
                              </executions>
                              <configuration>
                                <propertyPrefix>parsedVersionCustom</propertyPrefix>
                              </configuration>
                            </plugin>
                          </plugins>
                        </build>
                    """.trimIndent())
    maven.fixture.configureFromExistingVirtualFile(maven.projectPom)
    // Resolving this property depends on the presence of the build-helper-maven-plugin in pom. Reimport to add the plugin in MavenProject.
    runBlocking { maven.importProjectAsync() }
    maven.assertResolved(maven.projectPom, maven.findTag(maven.projectPom, "project.version"))
  }

  @Test
  fun testNotUpperCaseEnvPropertiesOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.Path}</name>
                       """.trimIndent())

    maven.assertUnresolved(maven.projectPom)
  }


  @Test
  fun testHighlightUnresolvedProperties() = runBlocking {
    maven.updateProjectPom("""
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
                       ${'$'}{parsedVersion.majorVersion}
                       </foo>
                       </properties>
                       """.trimIndent()
    )

    maven.checkHighlighting(
      maven.projectPom,
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
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>./parent/pom.xml</relativePath>
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
                       """.trimIndent())

    maven.createModulePom("parent",
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

    maven.createProfilesXml("parent",
                      """
                        <profile>
                          <id>one</id>
                          <properties>
                            <parentProfilesXmlProp>value</parentProfilesXmlProp>
                          </properties>
                        </profile>
                        """.trimIndent())

    maven.updateSettingsXml("""
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

    maven.updateProjectPom("""
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

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertContain(variants, "pomProp", "pomProfilesProp")
    assertContain(variants, "parentPomProp", "parentPomProfilesProp")
    assertContain(variants, "artifactId", "project.artifactId", "pom.artifactId")
    assertContain(variants, "basedir", "project.basedir", "pom.basedir", "project.baseUri", "pom.basedir")
    assertDoNotContain(variants, "baseUri")
    assertContain(variants, "build.timestamp")
    assertContain(variants, "maven.build.timestamp")
    assertContain(variants, "maven.multiModuleProjectDirectory")
    assertContain(variants, "maven.home")
    assertContain(variants, "maven.version")
    assertContain(variants, "maven.build.version")
    assertDoNotContain(variants, "project.maven.build.timestamp")
    assertContain(variants, "settingsXmlProp")
    assertContain(variants, "settings.localRepository")
    assertContain(variants, "user.home", "env.${maven.envVar}")
  }

  @Test
  fun testDoNotIncludeCollectionPropertiesInCompletion() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())
    maven.assertCompletionVariantsDoNotInclude(maven.projectPom, "project.dependencies", "env.\\=C\\:", "idea.config.path")
  }

  @Test
  fun testCompletingAfterOpenBrace() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret></name>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceInOpenTag() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>
                       """.trimIndent())

    maven.assertCompletionVariantsInclude(maven.projectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeText() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{pro<caret></name>
                       """.trimIndent())

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertContain(variants, "project.groupId")
    assertDoNotContain(variants, "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeTextWithDot() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.g<caret></name>
                       """.trimIndent())

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertContain(variants, "project.groupId")
    assertDoNotContain(variants, "project.name")
  }

  @Test
  fun testDoNotCompleteAfterNonWordCharacter() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<<caret>/name>
                       """.trimIndent())

    maven.assertCompletionVariantsDoNotInclude(maven.projectPom, "project.groupId")
  }


  @Test
  fun testCompletingMaven4Specific() = runBlocking {
    maven.assumeModel_4_1_0("applicable for maven4")
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.<caret></name>
                       """.trimIndent())

    val variants = maven.getCompletionVariants(maven.projectPom)
    assertContain(variants, "project.rootDirectory")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{session.<caret></name>
                       """.trimIndent())
    val sessionVariants = maven.getCompletionVariants(maven.projectPom)
    assertContain(sessionVariants, "session.rootDirectory", "session.topDirectory")
  }

  @Test
  fun testResolveMaven4SpecificRootDir() = runBlocking {
    maven.assumeModel_4_1_0("applicable for maven4")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                           <myProp>${'$'}{<caret>project.rootDirectory}</myProp>
                       </properties>

                       """.trimIndent())

    val rootDirectory = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent())!! }
    maven.assertResolved(maven.projectPom, rootDirectory)
  }

  @Test
  fun testResolveMaven4SpecificRootDirForSubmodules() = runBlocking {
    maven.assumeModel_4_1_0("applicable for maven4")
    maven.createModulePom("m1",
                    """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    maven.updateAllProjects()

    val m1 = maven.updateModulePom("m1",
                             """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       <properties>
                                         <myDir>${'$'}{<caret>project.rootDirectory}</myDir>
                                       </properties>
                                       """.trimIndent())
    val rootDirectory = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent()) }
    maven.assertResolved(m1, rootDirectory!!)
  }

  @Test
  fun testResolveMaven4SpecificSessionRootDirForSubmodules() = runBlocking {
    maven.assumeModel_4_1_0("applicable for maven4")
    maven.createModulePom("m1",
                    """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    maven.updateAllProjects()

    val m1 = maven.updateModulePom("m1",
                             """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       <properties>
                                         <myDir>${'$'}{<caret>session.rootDirectory}</myDir>
                                       </properties>
                                       """.trimIndent())
    val rootDirectory = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent()) }
    maven.assertResolved(m1, rootDirectory!!)
  }

  @Test
  fun testResolveMaven4SpecificSessionTopDirForSubmodules() = runBlocking {
    maven.assumeModel_4_1_0("applicable for maven4")
    maven.createModulePom("m1",
                    """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       """.trimIndent())
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    maven.updateAllProjects()

    val m1 = maven.updateModulePom("m1",
                             """
                                       <parent>
                                          <groupId>test</groupId>
                                          <artifactId>project</artifactId>
                                          <version>1</version>
                                       </parent>
                                       <artifactId>m1</artifactId>
                                       <properties>
                                         <myDir>${'$'}{<caret>session.topDirectory}</myDir>
                                       </properties>
                                       """.trimIndent())
    val rootDirectory = readAction { PsiManager.getInstance(maven.project).findDirectory(maven.projectPom.getParent()) }
    maven.assertResolved(m1, rootDirectory!!)
  }

  private suspend fun readWithProfiles(vararg profiles: String) {
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf(*profiles))
    maven.updateAllProjects()
  }
}
