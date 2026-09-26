// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.ui

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenConfigurationProducer
import org.jetbrains.idea.maven.execution.MavenGoalLocation
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenContextRunConfigurationTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var myNavigator: MavenProjectsNavigator

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.initProjectsManager(false)
    myNavigator = MavenProjectsNavigator.getInstance(maven.project)
    withContext(Dispatchers.EDT) {
      myNavigator.initForTests()
    }
    myNavigator.groupModules = true
  }

  @Test
  fun testCreateMavenRunConfigurationFromToolWindow() = runBlocking {
    val projectPom = maven.createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  """.trimIndent())
    maven.projectsManager.state.originalFiles = listOf(maven.projectPom.path)
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles.NONE
    maven.updateAllProjects()
    maven.projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val runConfiguration = createRunConfiguration(maven.projectPom, "validate")
        assertNotNull(runConfiguration)
        assertEquals("project [validate]", runConfiguration!!.name)
      }
    }
  }

  @Test
  fun testCheckMavenRunConfigurationFromToolWindow() = runBlocking {
    val projectPom = maven.createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  """.trimIndent())
    maven.projectsManager.state.originalFiles = listOf(maven.projectPom.path)
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles.NONE
    maven.updateAllProjects()
    maven.projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiFile = PsiManager.getInstance(maven.project).findFile(maven.projectPom)
        val context = ConfigurationContext.createEmptyContextForLocation(
          MavenGoalLocation(maven.project, psiFile, arrayOf("validate").asList()))
        val configurationFromContext = RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).createConfigurationFromContext(context)
        assertNotNull(configurationFromContext)
        val runConfiguration = configurationFromContext?.configuration as? MavenRunConfiguration
        assertNotNull(runConfiguration)
        assertTrue(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration!!, context))
      }
    }

  }


  @Test
  fun testMavenRunConfigurationFromToolWindowShouldBeDifferent(): Unit = runBlocking {
    maven.createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
  <modules>
    <module>m1</module>
    <module>m2</module>
  </modules>
  """)
    val m1 = maven.createModulePom("m1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m1</artifactId>""")
    val m2 = maven.createModulePom("m2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m2</artifactId>""")
    maven.projectsManager.state.originalFiles = (listOf(maven.projectPom, m2, m2)).map { it.path }
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles.NONE
    maven.updateAllProjects()
    maven.projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiFile = PsiManager.getInstance(maven.project).findFile(m1)
        val context = ConfigurationContext.createEmptyContextForLocation(
          MavenGoalLocation(maven.project, psiFile, arrayOf("validate").asList()))
        val configurationFromContext = RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).createConfigurationFromContext(context)
        assertNotNull(configurationFromContext)
        val runConfiguration = configurationFromContext?.configuration as? MavenRunConfiguration
        val psiFile2 = PsiManager.getInstance(maven.project).findFile(m2)
        val context2 = ConfigurationContext.createEmptyContextForLocation(
          MavenGoalLocation(maven.project, psiFile2, arrayOf("validate").asList()))
        val configurationFromContext2 = RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).createConfigurationFromContext(context2)
        val runConfiguration2 = configurationFromContext2?.configuration as? MavenRunConfiguration

        assertTrue(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration!!, context))

        assertTrue(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration2!!, context2))

        assertFalse(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration2, context))

        assertFalse(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration, context2))
      }
    }
  }


  @Test
  fun testMavenRunConfigurationFromToolWindowForMultimodule() = runBlocking {
    maven.createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
  <modules>
    <module>m1</module>
    <module>m2</module>
  </modules>
  """)
    val m1 = maven.createModulePom("m1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m1</artifactId>""")
    val m2 = maven.createModulePom("m2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m2</artifactId>""")
    maven.projectsManager.state.originalFiles = listOf(maven.projectPom, m2, m2).map { it.path }
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles.NONE
    maven.updateAllProjects()
    maven.projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val runConfiguration1 = createRunConfiguration(m1, "validate")
        assertNotNull(runConfiguration1)
        assertEquals(maven.mn("project", "m1") + " [validate]", runConfiguration1!!.name)
        val runConfiguration2 = createRunConfiguration(m2, "validate")
        assertEquals(maven.mn("project", "m2") + " [validate]", runConfiguration2!!.name)
      }
    }
  }


  private fun createRunConfiguration(projectPom: VirtualFile, vararg goals: String): MavenRunConfiguration? {
    val psiFile = PsiManager.getInstance(maven.project).findFile(projectPom)
    val context = ConfigurationContext.createEmptyContextForLocation(
      MavenGoalLocation(maven.project, psiFile, goals.asList()))
    val configurationFromContext = RunConfigurationProducer.getInstance(
      MavenConfigurationProducer::class.java).createConfigurationFromContext(context)
    assertNotNull(configurationFromContext)
    return configurationFromContext?.configuration as? MavenRunConfiguration
  }

}