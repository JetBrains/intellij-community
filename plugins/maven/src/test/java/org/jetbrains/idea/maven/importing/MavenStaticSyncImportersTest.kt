// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.junit.Test

class MavenStaticSyncImportersTest : AbstractMavenStaticSyncTest() {

  private lateinit var myImporter: MyMavenPluginImporter

  override fun setUp() {
    super.setUp()
    myImporter = MyMavenPluginImporter()
    ExtensionTestUtil.addExtensions(MavenImporter.EXTENSION_POINT_NAME, listOf(myImporter), testRootDisposable)
  }

  @Test
  fun `test plugin configuration is properly interpolated`() = runBlocking {
    myImporter.expect("myplugin", "myartifact")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <some.property>1</some.property>
                        <another.property>qwerty</another.property>
                    </properties>
                    <build>
                        <plugins>
                             <plugin>
                              <groupId>myplugin</groupId>
                              <artifactId>myartifact</artifactId>
                              <version>1/version>
                              <configuration>
                                <configProperty>${'$'}{some.property}</scalaVersion>
                                <nestedProperty>
                                    <nestedValue>${'$'}{another.property}</nestedValue>
                                </nestedProperty>
                              </configuration>
                            </plugin>
                        </plugins>
                    </build>
                    """.trimIndent())


    assertModules("project")
    UsefulTestCase.assertEquals(1, myImporter.mavenProjects.size)
    val mavenProject = myImporter.mavenProjects[0]
    val plugin = mavenProject.findPlugin("myplugin", "myartifact")
    TestCase.assertNotNull(plugin)
    UsefulTestCase.assertEquals("1", plugin!!.configurationElement!!.getChild("configProperty").textTrim)
    UsefulTestCase.assertEquals("qwerty", plugin.configurationElement!!.getChild("nestedProperty").getChild("nestedValue").textTrim)
  }

  @Test
  fun `test plugin execution configuration is properly interpolated`() = runBlocking {
    myImporter.expect("myplugin", "myartifact")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <my.value.one>one</my.value.one>
                        <my.value.two>two</my.value.two>
                    </properties>
                    <build>
                        <plugins>
                             <plugin>
                              <groupId>myplugin</groupId>
                              <artifactId>myartifact</artifactId>
                              <version>1/version>
                              <executions>
                                <execution>
                                  <id>some-exec</id>
                                  <phase>compile</phase>
                                  <goals>
                                    <goal>compile</goal>
                                  </goals>
                                  <configuration>
                                    <nestedList>
                                      <valueList>${'$'}{my.value.one}</valueList>
                                      <valueList>${'$'}{my.value.two}</valueList>
                                      <valueList>notInterpolatedIsAlsoOk</valueList>
                                    </excludes>
                                  </configuration>
                                </execution>
                              </executions>
                            </plugin>
                        </plugins>
                    </build>
                    """.trimIndent())


    assertModules("project")
    UsefulTestCase.assertEquals(1, myImporter.mavenProjects.size)
    val mavenProject = myImporter.mavenProjects[0]
    val plugin = mavenProject.findPlugin("myplugin", "myartifact")
    TestCase.assertNotNull(plugin)
    UsefulTestCase.assertContainsOrdered(plugin!!
                                           .executions
                                           .single { it.executionId == "some-exec" }
                                           .configurationElement!!.getChild("nestedList")!!
                                           .getChildren("valueList")!!.map { it.textTrim },
                                         "one", "two", "notInterpolatedIsAlsoOk"
    )
  }
}

class MyMavenPluginImporter : MavenImporter("", "") {
  var myGroupID: String? = null
  var myArtifactID: String? = null
  val mavenProjects = ArrayList<MavenProject>()
  fun expect(groupID: String, artifactId: String) {
    myGroupID = groupID
    myArtifactID = artifactId
  }

  override fun isApplicable(mavenProject: MavenProject): Boolean {
    return mavenProject.findPlugin(myGroupID, myArtifactID) != null
  }

  override fun process(modifiableModelsProvider: IdeModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject, changes: MavenProjectChanges, mavenProjectToModuleName: MutableMap<MavenProject, String>, postTasks: MutableList<MavenProjectsProcessorTask>) {
    mavenProjects.add(mavenProject)
  }
}
