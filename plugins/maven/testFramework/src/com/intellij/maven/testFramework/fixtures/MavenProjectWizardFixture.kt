// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.ide.projectWizard.ProjectWizardTestFixture
import com.intellij.ide.projectWizard.projectWizardFixture
import com.intellij.ide.wizard.Step
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator.NewProjectWizardFactory
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.replaceService
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenSyncListener
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardData
import org.jetbrains.idea.maven.wizards.MavenProjectImportProvider
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** A [ProjectWizardTestFixture] with the JDK and the Maven server cleanup that the Maven wizard tests need. */
interface MavenProjectWizardTestFixture {
  /** The project that receives new and imported modules. */
  val project: Project

  /** The internal JDK. The fixture registers it in the JDK table for the test. */
  val sdk: Sdk

  /** The wizard driver. */
  val wizards: ProjectWizardTestFixture
}

/** Creates a [MavenProjectWizardTestFixture] over an open project. It closes all Maven server connectors after the test. */
fun mavenProjectWizardFixture(
  projectFixture: TestFixture<Project> = projectFixture(openAfterCreation = true),
): TestFixture<MavenProjectWizardTestFixture> = testFixture("maven project wizard") {
  val wizards = projectWizardFixture(projectFixture).init()
  val sdk = ExternalSystemJdkProvider.getInstance().internalJdk
  wizards.addSdk(sdk)
  initialized(MavenProjectWizardTestFixtureImpl(wizards.project, sdk, wizards)) {
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
  }
}

private class MavenProjectWizardTestFixtureImpl(
  override val project: Project,
  override val sdk: Sdk,
  override val wizards: ProjectWizardTestFixture,
) : MavenProjectWizardTestFixture

/** The JDK that the wizard data holds as an existing JDK, or null for any other JDK intent. */
var MavenNewProjectWizardData.sdk: Sdk?
  get() = (jdkIntent as? ProjectWizardJdkIntent.ExistingJdk)?.jdk
  set(value) {
    jdkIntent = ProjectWizardJdkIntent.fromJdk(value)
  }

/** Writes a minimal `pom.xml` named [pomName] into a new directory and returns its path. */
fun MavenProjectWizardTestFixture.createPom(pomName: String = "pom.xml"): Path {
  val pom = Files.createTempDirectory(wizards.contentRoot, "pom").resolve(pomName)
  Files.writeString(pom, createPomXml(
    MavenConstants.MODEL_VERSION_4_0_0,
    """
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <version>1</version>
    """.trimIndent(),
    omitModelVersionTag = false))
  return pom
}

/** Writes `.mvn/wrapper/maven-wrapper.properties` with [content] next to [pomPath]. */
fun MavenProjectWizardTestFixture.createMavenWrapper(pomPath: Path, content: String) {
  val properties = pomPath.parent.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties")
  Files.createDirectories(properties.parent)
  Files.writeString(properties, content)
}

/** Imports the project at [path] with the [MavenProjectImportProvider] and waits for the Maven sync. */
suspend fun MavenProjectWizardTestFixture.importProjectFrom(path: Path): Module {
  return waitForImportWithinTimeout { wizards.importProjectFrom(path.toString(), null, MavenProjectImportProvider()) }
}

/** Imports the module at [path] into [MavenProjectWizardTestFixture.project] and waits for the Maven sync. */
suspend fun MavenProjectWizardTestFixture.importModuleFrom(path: Path): Module {
  return waitForImportWithinTimeout { wizards.importModuleFrom(MavenProjectImportProvider(), path.toString()) }
}

/** Runs [createProject] and waits for the Maven sync that the new project starts. */
suspend fun MavenProjectWizardTestFixture.waitForProjectCreation(createProject: suspend () -> Project): Project {
  return waitForImportWithinTimeout(createProject)
}

/** Runs [createModule] and waits for the Maven sync that the new module starts. */
suspend fun MavenProjectWizardTestFixture.waitForModuleCreation(createModule: suspend () -> Module): Module {
  return waitForImportWithinTimeout(createModule)
}

/** Runs [action] and waits until a Maven sync has started and finished. */
suspend fun <T> MavenProjectWizardTestFixture.waitForImportWithinTimeout(action: suspend () -> T): T {
  MavenLog.LOG.warn("waitForImportWithinTimeout started")
  val syncStarted = AtomicBoolean(false)
  val syncFinished = AtomicBoolean(false)
  ApplicationManager.getApplication().messageBus.connect(wizards.testDisposable)
    .subscribe(MavenSyncListener.TOPIC, object : MavenSyncListener {
      override fun syncStarted(project: Project) {
        syncStarted.set(true)
      }

      override fun syncFinished(project: Project) {
        syncFinished.set(true)
      }
    })

  val result = action()

  assertWithinTimeout {
    assertTrue(syncStarted.get() && syncFinished.get())
    MavenLog.LOG.warn("waitForImportWithinTimeout finished")
  }

  return result
}

/**
 * Runs [action] while the application uses a [NewProjectWizardFactory] whose wizard applies [configure] to every step
 * and finishes without UI. Call it on the EDT under the write-intent lock, as the code that opens the wizard requires.
 */
fun <R> MavenProjectWizardTestFixture.withWizard(action: () -> R, configure: Step.() -> Unit): R {
  val disposable = Disposer.newDisposable("withWizard")
  try {
    val factory = object : NewProjectWizardFactory {
      override fun create(project: Project?, modulesProvider: ModulesProvider, defaultPath: String?): NewProjectWizard {
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
  finally {
    Disposer.dispose(disposable)
  }
}
