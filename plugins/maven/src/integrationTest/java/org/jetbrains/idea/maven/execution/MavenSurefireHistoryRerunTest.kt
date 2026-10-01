// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * A test run that the IDE delegates to Maven lands in the test history as a plain `MavenRunConfiguration` entry.
 * The history rerun recreates the configuration from the configuration type, so the Surefire test mode must survive
 * a write-read cycle through that path.
 */
@TestApplication
class MavenSurefireHistoryRerunTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)
  }

  @Test
  fun `a test history entry recreates the surefire test run`() {
    val project = project.get()
    val testModuleDirectory = tempDir.get().resolve("module").toString()
    val source = MavenSurefireConfigurationFactory("MyTest", null).createTemplateConfiguration(project) as MavenSurefireRunConfiguration
    source.testModuleDirectory = testModuleDirectory
    source.reportSuffix = "42"
    source.runnerParameters.setGoals(listOf("-Dtest=MyTest", "test"))

    // The history file format of TestResultsXmlFormatter: a config element with the type id, then the test tree.
    val config = Element("config").setAttribute("configId", source.type.id).setAttribute("name", source.name)
    source.writeExternal(config)
    val historyFile = tempDir.get().resolve("MyTest.xml")
    JDOMUtil.write(Element("testrun").addContent(config).addContent(Element("root").setAttribute("name", "MyTest")), historyFile)

    val virtualFile = VfsUtil.findFile(historyFile, true) ?: fail("The history file $historyFile is not in the VFS")
    val restored = AbstractImportTestsAction.ImportRunProfile(virtualFile, project).initialConfiguration
    assertInstanceOf(MavenRunConfiguration::class.java, restored)
    restored as MavenRunConfiguration
    assertEquals(testModuleDirectory, restored.testModuleDirectory)
    assertEquals("42", restored.reportSuffix)
    assertEquals(listOf("-Dtest=MyTest", "test"), restored.runnerParameters.goals)

    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val environment = ExecutionEnvironmentBuilder(project, executor).runProfile(restored).build()
    assertInstanceOf(SurefireTestRunProfileState::class.java, restored.getState(executor, environment))
  }
}
