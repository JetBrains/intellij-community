// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.MyLoggingListener
import org.jetbrains.idea.maven.fixtures.createModulePom
import org.jetbrains.idea.maven.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.mavenEmbedderWrappers
import org.jetbrains.idea.maven.fixtures.mavenGeneralSettings
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.fixtures.log
import org.jetbrains.idea.maven.fixtures.rawProgressReporter
import org.jetbrains.idea.maven.fixtures.resolve
import org.jetbrains.idea.maven.fixtures.testRootDisposable
import org.jetbrains.idea.maven.fixtures.tree
import org.jetbrains.idea.maven.fixtures.updateAll
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsTree
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsTreeReadingPluginTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  @Test
  fun testDoNotUpdateChildAfterParentWasResolved() = runBlocking {
    maven.createProjectPom("""
                     <groupId>test</groupId>
                     <artifactId>parent</artifactId>
                     <version>1</version>
                     """.trimIndent())
    val child = maven.createModulePom("child",
                                """
                                <groupId>test</groupId>
                                <artifactId>child</artifactId>
                                <version>1</version>
                                <parent>
                                  <groupId>test</groupId>
                                  <artifactId>parent</artifactId>
                                  <version>1</version>
                                </parent>
                                """.trimIndent())
    val listener = MyLoggingListener()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, listener)
    maven.updateAll(maven.projectPom, child)
    val parentProject = maven.tree.findProject(maven.projectPom)!!

    maven.resolve(maven.project, parentProject, maven.mavenGeneralSettings)

    assertEquals(
      log()
        .add("updated", "parent", "child")
        .add("deleted")
        .add("resolved", "parent"),
      listener.log)
    maven.tree.updateAll(listOf(maven.projectPom, child), false, maven.mavenGeneralSettings, MavenExplicitProfiles.NONE, maven.mavenEmbedderWrappers, maven.rawProgressReporter)
    assertEquals(
      log()
        .add("updated", "parent", "child")
        .add("deleted")
        .add("resolved", "parent"),
      listener.log)
  }
}