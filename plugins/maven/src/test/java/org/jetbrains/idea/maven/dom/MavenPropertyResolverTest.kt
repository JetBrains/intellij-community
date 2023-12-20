/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import java.io.File

class MavenPropertyResolverTest : MavenMultiVersionImportingTestCase() {
  override fun runInDispatchThread() = true
  @Test
  fun testResolvingProjectAttributes() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("test", resolve("\${project.groupId}", myProjectPom))
    assertEquals("test", resolve("\${pom.groupId}", myProjectPom))
  }

  @Test
  fun testResolvingProjectParentAttributes() = runBlocking {
    val modulePom = createModulePom("test",
                                    """
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <parent>
                            <groupId>parent.test</groupId>
                            <artifactId>parent.project</artifactId>
                            <version>parent.1</version>
                          </parent>
                          """.trimIndent())
    importProjectAsync("""
                      <groupId>parent.test</groupId>
                      <artifactId>parent.project</artifactId>
                      <version>parent.1</version>
                      <packaging>pom</packaging>
                    <modules>
                      <module>test</module>
                    </modules>
                    """.trimIndent())

    assertEquals("parent.test", resolve("\${project.parent.groupId}", modulePom))
    assertEquals("parent.test", resolve("\${pom.parent.groupId}", modulePom))
  }

  @Test
  fun testResolvingAbsentProperties() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("\${project.parent.groupId}", resolve("\${project.parent.groupId}", myProjectPom))
  }

  @Test
  fun testResolvingProjectDirectories() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(File(projectPath, "target").path,
                 resolve("\${project.build.directory}", myProjectPom))
    assertEquals(File(projectPath, "src/main/java").path,
                 resolve("\${project.build.sourceDirectory}", myProjectPom))
  }

  @Test
  fun testResolvingProjectAndParentProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                        <parentProp>parent.value</parentProp>
                       </properties>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val f = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      <properties>
                                       <moduleProp>module.value</moduleProp>
                                      </properties>
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                        <version>1</version>
                                      </parent>
                                      """.trimIndent())

    importProjectAsync()

    assertEquals("parent.value", resolve("\${parentProp}", f))
    assertEquals("module.value", resolve("\${moduleProp}", f))

    assertEquals("\${project.parentProp}", resolve("\${project.parentProp}", f))
    assertEquals("\${pom.parentProp}", resolve("\${pom.parentProp}", f))
    assertEquals("\${project.moduleProp}", resolve("\${project.moduleProp}", f))
    assertEquals("\${pom.moduleProp}", resolve("\${pom.moduleProp}", f))
  }

  @Test
  fun testProjectPropertiesRecursively() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                        <prop1>value</prop1>
                        <prop2>${'$'}{prop1}-2</prop2>
                        <prop3>${'$'}{prop2}-3</prop3>
                       </properties>
                       """.trimIndent())

    importProjectAsync()

    assertEquals("value", resolve("\${prop1}", myProjectPom))
    assertEquals("value-2", resolve("\${prop2}", myProjectPom))
    assertEquals("value-2-3", resolve("\${prop3}", myProjectPom))
  }

  @Test
  fun testDoNotGoIntoInfiniteRecursion() = runBlocking {
    Assume.assumeTrue(isWorkspaceImport)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                        <prop1>${'$'}{prop1}</prop1>
                        <prop2>${'$'}{prop3}</prop2>
                        <prop3>${'$'}{prop2}</prop3>
                        <prop4>${'$'}{prop5}</prop4>
                        <prop5>${'$'}{prop6}</prop5>
                        <prop6>${'$'}{prop4}</prop6>
                       </properties>
                       """.trimIndent())

    importProjectAsync()
    assertEquals("\${prop1}", resolve("\${prop1}", myProjectPom))
    assertEquals("\${prop3}", resolve("\${prop3}", myProjectPom))
    assertEquals("\${prop5}", resolve("\${prop5}", myProjectPom))
  }

  @Test
  fun testSophisticatedPropertyNameDoesNotBreakResolver() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("\${~!@#$%^&*()}", resolve("\${~!@#$%^&*()}", myProjectPom))
    assertEquals("\${#ARRAY[@]}", resolve("\${#ARRAY[@]}", myProjectPom))
  }

  @Test
  fun testProjectPropertiesWithProfiles() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                        <prop>value1</prop>
                       </properties>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <prop>value2</prop>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <prop>value3</prop>
                           </properties>
                         </profile>
                       </profiles>
                       """.trimIndent())

    importProjectAsync()
    assertEquals("value1", resolve("\${prop}", myProjectPom))

    importProjectWithProfiles("one")
    assertEquals("value2", resolve("\${prop}", myProjectPom))

    importProjectWithProfiles("two")
    assertEquals("value3", resolve("\${prop}", myProjectPom))
  }

  @Test
  fun testResolvingBasedirProperties() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(projectPath, resolve("\${basedir}", myProjectPom))
    assertEquals(projectPath, resolve("\${project.basedir}", myProjectPom))
    assertEquals(projectPath, resolve("\${pom.basedir}", myProjectPom))
  }

  @Test
  fun testResolvingSystemProperties() = runBlocking {
    val javaHome = System.getProperty("java.home")
    val tempDir = System.getenv(getEnvVar())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(javaHome, resolve("\${java.home}", myProjectPom))
    assertEquals(tempDir, resolve("\${env." + getEnvVar() + "}", myProjectPom))
  }

  @Test
  fun testAllProperties() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("foo test-project bar",
                 resolve("foo \${project.groupId}-\${project.artifactId} bar", myProjectPom))
  }

  @Test
  fun testIncompleteProperties() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("\${project.groupId", resolve("\${project.groupId", myProjectPom))
    assertEquals("\$project.groupId}", resolve("\$project.groupId}", myProjectPom))
    assertEquals("{project.groupId}", resolve("{project.groupId}", myProjectPom))
  }

  @Test
  fun testUncomittedProperties() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val doc = FileDocumentManager.getInstance().getDocument(myProjectPom)
    WriteCommandAction.runWriteCommandAction(null) {
      doc!!.setText(createPomXml("""
                                                                                    <groupId>test</groupId>
                                                                                    <artifactId>project</artifactId>
                                                                                    <version>2</version>
                                                                                    <properties>
                                                                                      <uncomitted>value</uncomitted>
                                                                                    </properties>
                                                                                    """.trimIndent()))
    }

    PsiDocumentManager.getInstance(myProject).commitDocument(doc!!)

    assertEquals("value", resolve("\${uncomitted}", myProjectPom))
  }

  @Test
  fun testChainResolvePropertiesForFileWhichIsNotAProjectPom() = runBlocking {
    val file = createProjectSubFile("../some.pom",
                                    """
                                              <project>
                                                  <parent>
                                                      <groupId>org.example</groupId>
                                                      <artifactId>parent-id</artifactId>
                                                      <version>1.1</version>
                                                  </parent>
                                                  <artifactId>child</artifactId>
                                                  <properties>
                                                      <first>one</first>
                                                      <second>${'$'}{first}</second>
                                                      <third>${'$'}{second}${'$'}{parent.version}</third>
                                                  </properties>
                                              </project>
                                              """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("one", resolve("\${first}", file))
    assertEquals("one", resolve("\${second}", file))
    assertEquals("one1.1", resolve("\${third}", file))
    assertEquals("parent-id", resolve("\${parent.artifactId}", file))
  }

  private fun resolve(text: String, f: VirtualFile): String {
    return MavenPropertyResolver.resolve(text, MavenDomUtil.getMavenDomProjectModel(myProject, f))
  }
}

