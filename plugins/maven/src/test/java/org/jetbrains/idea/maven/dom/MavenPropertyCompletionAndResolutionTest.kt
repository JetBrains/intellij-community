// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.properties.IProperty
import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles
import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testResolutionToProjectAt() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>@<caret>project.version@</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testCorrectlyCalculatingTextRangeWithLeadingWhitespaces() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>     ${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testBuiltInBasedirProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>basedir}</name>
                       """.trimIndent())

    val baseDir = readAction { PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())!! }
    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, baseDir)
    }

    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.basedir}</name>
                       """.trimIndent())


    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, baseDir)
    }

    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.basedir}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, baseDir)
    }
  }

  @Test
  fun testBuiltInMavenMultimoduleDirProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <myDir>${'$'}{<caret>maven.multiModuleProjectDirectory}</myDir>
                       </properties>>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      val multimoduleDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())
      assertResolved(myProjectPom, multimoduleDir!!)
    }
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
    importProjectAsync("""
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
    withContext(Dispatchers.EDT) {
      val multimoduleDir = PsiManager.getInstance(myProject).findDirectory(myProjectPom.getParent())
      assertResolved(m1, multimoduleDir!!)
    }
  }

  @Test
  fun testResolutionWithSeveralProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.artifactId}-${'$'}{project.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.artifactId"))
    }

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{project.artifactId}-${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testResolvingFromPropertiesSection() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>${'$'}{<caret>project.version}</foo>
                       </properties>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testResolvingFromPropertiesSectionAt() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>@<caret>project.version@</foo>
                       </properties>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testResolutionToUnknownProjectProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.bar}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertUnresolved(myProjectPom)
    }
  }

  @Test
  fun testResolutionToAbsentProjectProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.description}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.name"))
    }
  }

  @Test
  fun testResolutionToAbsentPomProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.description}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.name"))
    }
  }

  @Test
  fun testResolutionToAbsentUnclassifiedProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>description}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.name"))
    }
  }

  @Test
  fun testResolutionToPomProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>pom.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testResolutionToUnclassifiedProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.version"))
    }
  }

  @Test
  fun testResolutionToDerivedCoordinatesFromProjectParent() = runBlocking {
    createProjectPom("""
                       <artifactId>project</artifactId>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.parent.version"))
    }
  }

  @Test
  fun testResolutionToProjectParent() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId  <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{<caret>project.parent.version}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag("project.parent.version"))
    }
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForManagedParent() = runBlocking {
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
    importProjectsAsync(myProjectPom, child)

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

    withContext(Dispatchers.EDT) {
      assertResolved(child, findTag(myProjectPom, "project.build.directory"))
    }
  }

  @Test
  fun testResolutionToInheritedModelPropertiesForRelativeParent() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(parent, "project.build.directory"))
    }
  }

  @Test
  fun testResolutionToInheritedPropertiesForNonManagedParent() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(parent, "project.properties.foo"))
    }
  }

  @Test
  fun testResolutionToInheritedSuperPomProjectProperty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>project.build.finalName}</name>
                       """.trimIndent())

    val effectiveSuperPom = MavenUtil.getEffectiveSuperPom(myProject, myProjectRoot.toNioPath().toString())
    assertNotNull(effectiveSuperPom)
    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(effectiveSuperPom, "project.build.finalName"))
    }
  }

  @Test
  fun testHandleResolutionRecursion() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.name"))
    }
  }

  @Test
  fun testResolutionFromProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <foo>value</foo>
                       </properties>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"))
    }
  }

  @Test
  fun testResolutionWithProfiles() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
    }
  }

  @Test
  fun testResolutionToPropertyDefinedWithinProfiles() = runBlocking {
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
    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
    }
  }

  @Test
  fun testResolutionToPropertyDefinedOutsideProfiles() = runBlocking {
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
    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.properties.foo"))
    }
  }

  @Test
  fun testResolutionWithDefaultProfiles() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
    }
  }

  @Test
  fun testResolutionWithTriggeredProfiles() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[1].properties.foo"))
    }
  }

  @Test
  fun testResolvingToProfilesBeforeModelsProperties() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.profiles[0].properties.foo"))
    }
  }

  @Test
  fun testResolvingPropertiesInSettingsXml() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(profiles, "settings.profiles[1].properties.foo", MavenDomSettingsModel::class.java))
    }
  }

  @Test
  fun testResolvingSettingsModelProperties() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(profiles, "settings.localRepository", MavenDomSettingsModel::class.java))
    }
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

    fixture.configureFromExistingVirtualFile(profiles)
    readAction {
      val elementAtCaret = fixture.getElementAtCaret()
      assert(elementAtCaret is XmlTag)
      assertEquals("foo", (elementAtCaret as XmlTag).getName())
    }
  }

  @Test
  fun testResolvingAbsentSettingsModelProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.localRepository}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(myProjectPom, "project.name"))
    }
  }

  @Test
  fun testResolvingUnknownSettingsModelProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>settings.foo.bar}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertUnresolved(myProjectPom)
    }
  }

  @Test
  fun testResolvingPropertiesInProfilesXml() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(profiles, "profilesXml.profiles[1].properties.foo", MavenDomProfilesModel::class.java))
    }
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

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    readWithProfiles("two")

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(profiles, "profiles[1].properties.foo", MavenDomProfiles::class.java))
    }
  }

  @Test
  fun testResolvingInheritedProperties() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, findTag(parent, "project.properties.foo"))
    }
  }

  @Test
  fun testSystemProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>user.home}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom,
                     MavenPropertiesVirtualFileSystem.getInstance().findSystemProperty(myProject, "user.home")!!.getPsiElement())
    }
  }

  @Test
  fun testEnvProperties() = runBlocking {
    createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <name>${"$"}{<caret>env.${getEnvVar()}}</name>
  """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertResolved(myProjectPom, MavenPropertiesVirtualFileSystem.getInstance().findEnvProperty(myProject, getEnvVar())!!.getPsiElement())
    }
  }

  @Test
  fun testUpperCaseEnvPropertiesOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PATH}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      val ref = getReferenceAtCaret(myProjectPom)
      assertNotNull(ref)

      val resolved = ref!!.resolve()
      assertEquals(System.getenv("Path").replace("[^A-Za-z]".toRegex(), ""),
                   (resolved as IProperty?)!!.getValue()!!.replace("[^A-Za-z]".toRegex(), ""))
    }
  }

  @Test
  fun testCaseInsencitiveOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.PaTH}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertUnresolved(myProjectPom)
    }
  }

  @Test
  fun testNotUpperCaseEnvPropertiesOnWindows() = runBlocking {
    if (!SystemInfo.isWindows) return@runBlocking

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>env.Path}</name>
                       """.trimIndent())

    withContext(Dispatchers.EDT) {
      assertUnresolved(myProjectPom)
    }
  }

  @Test
  fun testHighlightUnresolvedProperties() = runBlocking {
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

    withContext(Dispatchers.EDT) {
      checkHighlighting()
    }
  }

  @Test
  fun testCompletion() = runBlocking {
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
  fun testDoNotIncludeCollectionPropertiesInCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())
    assertCompletionVariantsDoNotInclude(myProjectPom, "project.dependencies", "env.\\=C\\:", "idea.config.path")
  }

  @Test
  fun testCompletingAfterOpenBrace() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret></name>
                       """.trimIndent())

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceInOpenTag() = runBlocking {
    if (ignore()) return@runBlocking

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>
                       """.trimIndent())

    assertCompletionVariantsInclude(myProjectPom, "project.groupId", "groupId")
  }

  @Test
  fun testCompletingAfterOpenBraceAndSomeText() = runBlocking {
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
  fun testCompletingAfterOpenBraceAndSomeTextWithDot() = runBlocking {
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
  fun testDoNotCompleteAfterNonWordCharacter() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<<caret>/name>
                       """.trimIndent())

    assertCompletionVariantsDoNotInclude(myProjectPom, "project.groupId")
  }

  private suspend fun readWithProfiles(vararg profiles: String) {
    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf(*profiles))
    updateAllProjects()
  }

  override fun readProjects() = runBlocking {
    readWithProfiles()
  }
}
