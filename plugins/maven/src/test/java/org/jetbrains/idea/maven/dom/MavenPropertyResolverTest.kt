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

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.envVar
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Paths

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPropertyResolverTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testResolvingProjectAttributes() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("test", resolve("\${project.groupId}", maven.projectPom))
    assertEquals("test", resolve("\${pom.groupId}", maven.projectPom))
  }

  @Test
  fun testResolvingProjectParentAttributes() = runBlocking {
    val modulePom = maven.createModulePom("test",
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
    maven.importProjectAsync("""
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
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("\${project.parent.groupId}", resolve("\${project.parent.groupId}", maven.projectPom))
  }

  @Test
  fun testResolvingProjectDirectories() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(Paths.get(maven.projectPath.toString(), "target").toString(),
                 resolve("\${project.build.directory}", maven.projectPom))
    assertEquals(Paths.get(maven.projectPath.toString(), "src/main/java").toString(),
                 resolve("\${project.build.sourceDirectory}", maven.projectPom))
  }

  @Test
  fun testResolvingProjectAndParentProperties() = runBlocking {
    maven.createProjectPom("""
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

    val f = maven.createModulePom("m",
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

    maven.importProjectAsync()

    assertEquals("parent.value", resolve("\${parentProp}", f))
    assertEquals("module.value", resolve("\${moduleProp}", f))

    assertEquals("\${project.parentProp}", resolve("\${project.parentProp}", f))
    assertEquals("\${pom.parentProp}", resolve("\${pom.parentProp}", f))
    assertEquals("\${project.moduleProp}", resolve("\${project.moduleProp}", f))
    assertEquals("\${pom.moduleProp}", resolve("\${pom.moduleProp}", f))
  }

  @Test
  fun testProjectPropertiesRecursively() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                        <prop1>value</prop1>
                        <prop2>${'$'}{prop1}-2</prop2>
                        <prop3>${'$'}{prop2}-3</prop3>
                       </properties>
                       """.trimIndent())

    maven.importProjectAsync()

    assertEquals("value", resolve("\${prop1}", maven.projectPom))
    assertEquals("value-2", resolve("\${prop2}", maven.projectPom))
    assertEquals("value-2-3", resolve("\${prop3}", maven.projectPom))
  }

  @Test
  fun testDoNotGoIntoInfiniteRecursion() = runBlocking {
    maven.createProjectPom("""
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

    maven.importProjectAsync()
    assertEquals("\${prop1}", resolve("\${prop1}", maven.projectPom))
    assertEquals("\${prop3}", resolve("\${prop3}", maven.projectPom))
    assertEquals("\${prop5}", resolve("\${prop5}", maven.projectPom))
  }

  @Test
  fun testSophisticatedPropertyNameDoesNotBreakResolver() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("\${~!@#$%^&*()}", resolve("\${~!@#$%^&*()}", maven.projectPom))
    assertEquals("\${#ARRAY[@]}", resolve("\${#ARRAY[@]}", maven.projectPom))
  }

  @Test
  fun testProjectPropertiesWithProfiles() = runBlocking {
    maven.createProjectPom("""
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

    maven.importProjectAsync()
    assertEquals("value1", resolve("\${prop}", maven.projectPom))

    maven.importProjectWithProfiles("one")
    assertEquals("value2", resolve("\${prop}", maven.projectPom))

    maven.importProjectWithProfiles("two")
    assertEquals("value3", resolve("\${prop}", maven.projectPom))
  }

  @Test
  fun testResolvingBasedirProperties() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(maven.projectPath.toCanonicalPath().toString(), resolve("\${basedir}", maven.projectPom))
    assertEquals(maven.projectPath.toCanonicalPath().toString(), resolve("\${project.basedir}", maven.projectPom))
    assertEquals(maven.projectPath.toCanonicalPath().toString(), resolve("\${pom.basedir}", maven.projectPom))
  }

  @Test
  fun testResolvingSystemProperties() = runBlocking {
    val javaHome = System.getProperty("java.home")
    val tempDir = System.getenv(maven.envVar)

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(javaHome, resolve("\${java.home}", maven.projectPom))
    assertEquals(tempDir, resolve("\${env." + maven.envVar + "}", maven.projectPom))
  }

  @Test
  fun testAllProperties() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("foo test-project bar",
                 resolve("foo \${project.groupId}-\${project.artifactId} bar", maven.projectPom))
  }

  @Test
  fun testIncompleteProperties() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("\${project.groupId", resolve("\${project.groupId", maven.projectPom))
    assertEquals("\$project.groupId}", resolve("\$project.groupId}", maven.projectPom))
    assertEquals("{project.groupId}", resolve("{project.groupId}", maven.projectPom))
  }

  @Test
  fun testUncomittedProperties() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val doc = readAction { FileDocumentManager.getInstance().getDocument(maven.projectPom) }
    edtWriteAction {
      doc!!.setText(maven.createPomXml("""
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>2</version>
          <properties>
            <uncomitted>value</uncomitted>
          </properties>
          """.trimIndent()))
      PsiDocumentManager.getInstance(maven.project).commitDocument(doc)
    }

    assertEquals("value", resolve("\${uncomitted}", maven.projectPom))
  }

  @Test
  fun testChainResolvePropertiesForFileWhichIsNotAProjectPom() = runBlocking {
    val file = maven.createProjectSubFile("../some.pom",
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

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals("one", resolve("\${first}", file))
    assertEquals("one", resolve("\${second}", file))
    assertEquals("one1.1", resolve("\${third}", file))
    assertEquals("parent-id", resolve("\${parent.artifactId}", file))
  }

  @Test
  fun testResolveMavenCoordinatesWithDependencyPropertiesPlugin() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                          <groupId>mygroup</groupId>
                          <artifactId>myartifact</artifactId>
                          <version>1.0</version>
                      </dependency>
                      <dependency>
                          <groupId>anothergroup</groupId>
                          <artifactId>anotherartifact</artifactId>
                          <version>1.0</version>
                          <type>type</type>
                          <classifier>classifier</classifier>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                           <groupId>org.apache.maven.plugins</groupId>
                           <artifactId>maven-dependency-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>properties</goal>
                               </goals>
                             </execution>
                           </executions>
                          </plugin>
                       </plugins>
                    </build>
                    """.trimIndent())
    val pathFirst = maven.repositoryPath.resolve("mygroup/myartifact/1.0/myartifact-1.0.jar").toAbsolutePath().toString()
    val pathAnother = maven.repositoryPath.resolve("anothergroup/anotherartifact/1.0/anotherartifact-1.0-classifier.type").toAbsolutePath().toString()
    assertEquals(pathFirst, resolve("\${mygroup:myartifact:jar}", maven.projectPom))
    assertEquals(pathAnother, resolve("\${anothergroup:anotherartifact:type:classifier}", maven.projectPom))

  }

  private suspend fun resolve(text: String, f: VirtualFile): String {
    return readAction { MavenPropertyResolver.resolve(text, MavenDomUtil.getMavenDomProjectModel(maven.project, f)) }
  }
}
