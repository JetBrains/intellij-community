// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.ui

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunAll
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenConfigurationProducer
import org.jetbrains.idea.maven.execution.MavenGoalLocation
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.junit.Test

class MavenContextRunConfigurationTest : MavenDomTestCase() {
  private lateinit var myNavigator: MavenProjectsNavigator

  public override fun setUp() = runBlocking {
    super.setUp()
    initProjectsManager(false)
    myNavigator = MavenProjectsNavigator.getInstance(project)
    withContext(Dispatchers.EDT) {
      myNavigator.initForTests()
    }
    myNavigator.groupModules = true
  }

  public override fun tearDown() {
    RunAll.runAll({
                    waitForMavenUtilRunnablesComplete()
                  },
                  { super.tearDown() })
  }

  @Test
  fun testCreateMavenRunConfigurationFromToolWindow() = runBlocking {
    val projectPom = createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  """.trimIndent())
    projectsManager.projectsTree.resetManagedFilesAndProfiles(listOf(projectPom), MavenExplicitProfiles.NONE)
    updateAllProjects()
    projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val runConfiguration = createRunConfiguration(projectPom, "validate")
        TestCase.assertNotNull(runConfiguration)
        TestCase.assertEquals("project [validate]", runConfiguration!!.name)
      }
    }
  }

  @Test
  fun testCheckMavenRunConfigurationFromToolWindow() = runBlocking {
    val projectPom = createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  """.trimIndent())
    projectsManager.projectsTree.resetManagedFilesAndProfiles(listOf(projectPom), MavenExplicitProfiles.NONE)
    updateAllProjects()
    projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiFile = PsiManager.getInstance(project).findFile(projectPom)
        val context = ConfigurationContext.createEmptyContextForLocation(
          MavenGoalLocation(project, psiFile, arrayOf("validate").asList()))
        val configurationFromContext = RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).createConfigurationFromContext(context)
        TestCase.assertNotNull(configurationFromContext)
        val runConfiguration = configurationFromContext?.configuration as? MavenRunConfiguration
        TestCase.assertNotNull(runConfiguration)
        TestCase.assertTrue(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration!!, context))
      }
    }

  }


  @Test
  fun testMavenRunConfigurationFromToolWindowShouldBeDifferent() = runBlocking {
    createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
  <modules>
    <module>m1</module>
    <module>m2</module>
  </modules>
  """)
    val m1 = createModulePom("m1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m1</artifactId>""")
    val m2 = createModulePom("m2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m2</artifactId>""")
    projectsManager.projectsTree.resetManagedFilesAndProfiles(listOf(projectPom, m2, m2), MavenExplicitProfiles.NONE)
    updateAllProjects()
    projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiFile = PsiManager.getInstance(project).findFile(m1)
        val context = ConfigurationContext.createEmptyContextForLocation(
          MavenGoalLocation(project, psiFile, arrayOf("validate").asList()))
        val configurationFromContext = RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).createConfigurationFromContext(context)
        TestCase.assertNotNull(configurationFromContext)
        val runConfiguration = configurationFromContext?.configuration as? MavenRunConfiguration
        val psiFile2 = PsiManager.getInstance(project).findFile(m2)
        val context2 = ConfigurationContext.createEmptyContextForLocation(
          MavenGoalLocation(project, psiFile2, arrayOf("validate").asList()))
        val configurationFromContext2 = RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).createConfigurationFromContext(context2)
        val runConfiguration2 = configurationFromContext2?.configuration as? MavenRunConfiguration

        TestCase.assertTrue(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration!!, context))

        TestCase.assertTrue(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration2!!, context2))

        TestCase.assertFalse(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration2, context))

        TestCase.assertFalse(RunConfigurationProducer.getInstance(
          MavenConfigurationProducer::class.java).isConfigurationFromContext(runConfiguration, context2))
      }
    }
  }


  @Test
  fun testMavenRunConfigurationFromToolWindowForMultimodule() = runBlocking {
    createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
  <modules>
    <module>m1</module>
    <module>m2</module>
  </modules>
  """)
    val m1 = createModulePom("m1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m1</artifactId>""")
    val m2 = createModulePom("m2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>m2</artifactId>""")
    projectsManager.projectsTree.resetManagedFilesAndProfiles(listOf(projectPom, m2, m2), MavenExplicitProfiles.NONE)
    updateAllProjects()
    projectsManager.fireActivatedInTests()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val runConfiguration1 = createRunConfiguration(m1, "validate")
        TestCase.assertNotNull(runConfiguration1)
        TestCase.assertEquals(mn("project", "m1") + " [validate]", runConfiguration1!!.name)
        val runConfiguration2 = createRunConfiguration(m2, "validate")
        TestCase.assertEquals(mn("project", "m2") + " [validate]", runConfiguration2!!.name)
      }
    }
  }


  private fun createRunConfiguration(projectPom: VirtualFile, vararg goals: String): MavenRunConfiguration? {
    val psiFile = PsiManager.getInstance(project).findFile(projectPom)
    val context = ConfigurationContext.createEmptyContextForLocation(
      MavenGoalLocation(project, psiFile, goals.asList()))
    val configurationFromContext = RunConfigurationProducer.getInstance(
      MavenConfigurationProducer::class.java).createConfigurationFromContext(context)
    TestCase.assertNotNull(configurationFromContext)
    return configurationFromContext?.configuration as? MavenRunConfiguration
  }

}