// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.ide.projectWizard.NewProjectWizardTestCase
import com.intellij.ide.wizard.Step
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator.NewProjectWizardFactory
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.closeOpenedProjectsIfFail
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.vfs.getFile
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Consumer

abstract class MavenNewProjectWizardTestCase : NewProjectWizardTestCase() {
  lateinit var mySdk: Sdk
  private lateinit var testDisposable: Disposable

  override fun setUp() {
    super.setUp()
    testDisposable = Disposer.newDisposable()

    mySdk = ExternalSystemJdkProvider.getInstance().internalJdk
    val jdkTable = ProjectJdkTable.getInstance()
    runWriteActionAndWait {
      jdkTable.addJdk(mySdk, testDisposable)
    }
    Disposer.register(testDisposable, Disposable {
      runWriteActionAndWait {
        jdkTable.removeJdk(mySdk)
      }
    })
  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(testDisposable) },
      { super.tearDown() },
    )
  }

  override fun createWizard(project: Project?) {
    if (project != null) {
      val localFileSystem = LocalFileSystem.getInstance()
      localFileSystem.refreshAndFindFileByPath(project.basePath!!)
    }
    val directory = if (project == null) createTempDirectoryWithSuffix("New").toFile() else null
    if (myWizard != null) {
      Disposer.dispose(myWizard.disposable)
      myWizard = null
    }
    myWizard = createWizard(project, directory)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  fun waitForMavenImporting(module: Module) {
    val pomFile = module.guessModuleDir()!!.getFile("pom.xml")
    waitForMavenImporting(module.project, pomFile)
  }

  fun waitForMavenImporting(project: Project, file: VirtualFile) {
    val manager = MavenProjectsManager.getInstance(project)
    if (!MavenUtil.isLinearImportEnabled()) {
      manager.waitForImportCompletion()
      ApplicationManager.getApplication().invokeAndWait {
        manager.scheduleImportInTests(listOf(file))
        manager.importProjects()
      }
    }
    val promise = manager.waitForImportCompletion()
    PlatformTestUtil.waitForPromise(promise)
  }

  override fun createProject(adjuster: Consumer<in Step>?): Project {
    return closeOpenedProjectsIfFail {
      super.createProject(adjuster)
    }
  }

  fun <R> withWizard(action: () -> R, configure: Step.() -> Unit): R {
    Disposer.newDisposable().use { disposable ->
      val factory = object : NewProjectWizardFactory {
        override fun create(project: Project?, modulesProvider: ModulesProvider): NewProjectWizard {
          return object : NewProjectWizard(project, modulesProvider, null) {
            override fun showAndGet(): Boolean {
              while (true) {
                val currentStep = currentStepObject
                currentStep.configure()
                if (isLast) break
                doNextAction()
                if (currentStep === currentStepObject) {
                  throw RuntimeException("$currentStepObject is not validated")
                }
              }
              if (!doFinishAction()) {
                throw RuntimeException("$currentStepObject is not validated")
              }
              return true
            }
          }
        }
      }
      ApplicationManager.getApplication().replaceService(NewProjectWizardFactory::class.java, factory, disposable)
      return action()
    }
  }
}