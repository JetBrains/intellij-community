// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.ide.projectWizard.NewProjectWizardFactory
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import org.junit.Test

class ModulesConfiguratorTest : MavenMultiVersionImportingTestCase() {
  private lateinit var projectStructureConfigurable: ProjectStructureConfigurable
  private lateinit var modulesConfigurator: ModulesConfigurator

  override fun setUp() {
    super.setUp()
    projectStructureConfigurable = ProjectStructureConfigurable.getInstance(myProject)
    modulesConfigurator = projectStructureConfigurable.context.modulesConfigurator
  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(projectStructureConfigurable) },
      { projectStructureConfigurable.disposeUIResources() },
      { super.tearDown() }
    )
  }

  @Test
  fun `test wizard creates module in project structure modifiable model`() {
    val wizard = NewProjectWizardFactory().create(myProject, modulesConfigurator)

    val projectBuilder = wizard.getBuilder(myProject)
    val moduleModel = modulesConfigurator.moduleModel

    val builderModules = projectBuilder!!.commit(myProject, moduleModel, modulesConfigurator)

    assertNotNull(builderModules)
    val modelModules = moduleModel.modules
    assertSameElements(builderModules!!, *modelModules)
    assertSize(1, modelModules)
    val module = modelModules[0]

    // verify there are no errors when the module is disposed
    moduleModel.disposeModule(module)
    Disposer.dispose(module)

    // dispose wizard
    Disposer.dispose(wizard.disposable)
  }

  @Test
  fun `test configurator creates module in project structure modifiable model`() {
    val configuratorModules = modulesConfigurator.addNewModule("untitled")

    assertNotNull(configuratorModules)
    val moduleModel = modulesConfigurator.moduleModel
    val modelModules = moduleModel.modules
    assertSameElements(configuratorModules!!, *modelModules)
    assertSize(1, modelModules)
    val module = modelModules[0]

    // verify there are no errors when the module is disposed
    val editor = modulesConfigurator.getModuleEditor(module)
    modulesConfigurator.deleteModules(listOf(editor))
    Disposer.dispose(module)
  }
}