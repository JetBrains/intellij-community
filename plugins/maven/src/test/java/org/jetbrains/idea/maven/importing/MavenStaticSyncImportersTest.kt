// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.PairConsumer
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.*
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.Test
import java.util.*
import java.util.stream.Stream

class MavenStaticSyncImportersTest : AbstractMavenStaticSyncTest() {

  private lateinit var myLegacyImporter: MyTestLegacyImporter
  private lateinit var myImporter: MyMavenPluginImporter

  override fun setUp() {
    super.setUp()
    myImporter = MyMavenPluginImporter()
    myLegacyImporter = MyTestLegacyImporter()
    ExtensionTestUtil.addExtensions(MavenWorkspaceConfigurator.EXTENSION_POINT_NAME, listOf(myImporter, MyAlwaysFailConfigurerDoNotImplementingStaticSyncAware()), testRootDisposable)
    ExtensionTestUtil.addExtensions(MavenImporter.EXTENSION_POINT_NAME, listOf(MyTestAlwaysFailLegacyImporter(), myLegacyImporter), testRootDisposable)
  }

  @Test
  fun `test plugin configuration is properly interpolated`() = runBlocking {
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
    UsefulTestCase.assertEquals(1, myLegacyImporter.mavenProjects.size)
    val mavenProject = myImporter.mavenProjects[0]
    val plugin = mavenProject.findPlugin("myplugin", "myartifact")
    TestCase.assertNotNull(plugin)
    UsefulTestCase.assertEquals("1", plugin!!.configurationElement!!.getChild("configProperty").textTrim)
    UsefulTestCase.assertEquals("qwerty", plugin.configurationElement!!.getChild("nestedProperty").getChild("nestedValue").textTrim)
  }

  @Test
  fun `test plugin execution configuration is properly interpolated`() = runBlocking {
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
    UsefulTestCase.assertEquals(1, myLegacyImporter.mavenProjects.size)
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

class MyMavenPluginImporter : MavenWorkspaceConfigurator, MavenStaticSyncAware {

  val mavenProjects = ArrayList<MavenProject>()


  override fun configureMavenProject(context: MavenWorkspaceConfigurator.MutableMavenProjectContext) {
    mavenProjects.add(context.mavenProjectWithModules.mavenProject)
  }
}


class MyTestLegacyImporter : MavenImporter("", ""), MavenStaticSyncAware {

  val mavenProjects = ArrayList<MavenProject>()
  override fun isApplicable(mavenProject: MavenProject?): Boolean {
    return true
  }

  override fun process(modifiableModelsProvider: IdeModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject, changes: MavenProjectChanges, mavenProjectToModuleName: MutableMap<MavenProject, String>, postTasks: MutableList<MavenProjectsProcessorTask>) {
    mavenProjects.add(mavenProject)
  }
}

private class MyAlwaysFailConfigurerDoNotImplementingStaticSyncAware : MavenWorkspaceConfigurator {

  override fun getAdditionalFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<MavenWorkspaceConfigurator.AdditionalFolder> {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getFoldersToExclude(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun configureMavenProject(context: MavenWorkspaceConfigurator.MutableMavenProjectContext) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun afterModelApplied(context: MavenWorkspaceConfigurator.AppliedModelContext) {
    throw IllegalStateException("Should never be called in static import")
  }
}


private class MyTestAlwaysFailLegacyImporter : MavenImporter("", "") {
  override fun isApplicable(mavenProject: MavenProject?): Boolean {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getModuleType(): ModuleType<out ModuleBuilder> {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getSupportedPackagings(result: MutableCollection<in String>?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getSupportedDependencyTypes(result: MutableCollection<in String>?, type: SupportedRequestType?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getSupportedDependencyScopes(result: MutableCollection<in String>?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getExtraArtifactClassifierAndExtension(artifact: MavenArtifact?, type: MavenExtraArtifactType?): Pair<String, String>? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun isMigratedToConfigurator(): Boolean {
    return true
  }

  override fun preProcess(module: Module?, mavenProject: MavenProject?, changes: MavenProjectChanges?, modifiableModelsProvider: IdeModifiableModelsProvider?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun process(modifiableModelsProvider: IdeModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject, changes: MavenProjectChanges, mavenProjectToModuleName: MutableMap<MavenProject, String>, postTasks: MutableList<MavenProjectsProcessorTask>) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun postProcess(module: Module?, mavenProject: MavenProject?, changes: MavenProjectChanges?, modifiableModelsProvider: IdeModifiableModelsProvider?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun processChangedModulesOnly(): Boolean {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun collectSourceRoots(mavenProject: MavenProject?, result: PairConsumer<String, JpsModuleSourceRootType<*>>?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun collectExcludedFolders(mavenProject: MavenProject?, result: MutableList<String>?) {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getConfig(p: MavenProject?): Element? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getConfig(p: MavenProject?, path: String?): Element? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun findConfigValue(p: MavenProject?, path: String?): String? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun findConfigValue(p: MavenProject?, path: String?, defaultValue: String?): String? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun getGoalConfig(p: MavenProject?, goal: String?): Element? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun findGoalConfigValue(p: MavenProject?, goal: String?, path: String?): String? {
    throw IllegalStateException("Should never be called in static import")
  }

  override fun customizeUserProperties(project: Project, mavenProject: MavenProject, properties: Properties) {
    throw IllegalStateException("Should never be called in static import")
  }
}
