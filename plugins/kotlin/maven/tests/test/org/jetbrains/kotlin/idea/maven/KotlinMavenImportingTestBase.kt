// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.assertSourceRoots
import com.intellij.maven.testFramework.fixtures.createStdProjectFolders
import com.intellij.maven.testFramework.fixtures.defaultResources
import com.intellij.maven.testFramework.fixtures.defaultTestResources
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModule
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.test.KotlinSdkCreationChecker
import org.jetbrains.kotlin.idea.test.resetCodeStyle
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory
import org.jetbrains.kotlin.platform.TargetPlatform
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.atomic.AtomicInteger

/**
 * JUnit 5 base for Kotlin Maven importing tests, replacing the legacy JUnit 3/4 `KotlinMavenImportingTestCase` and
 * `AbstractKotlinMavenImporterTest`. Concrete tests are parameterized over Maven versions; annotate the leaf class:
 * ```
 * @TestApplication
 * @ParameterizedClass
 * @ArgumentsSource(MavenVersionArguments::class)
 * class MyTest(mavenVersion: String, modelVersion: String) : KotlinMavenImportingTestBase(mavenVersion, modelVersion)
 * ```
 *
 * @param createStdProjectFolders when `true` (default), the standard `src/main`, `src/test` folders are created during
 *   set-up, mirroring `AbstractKotlinMavenImporterTest(createStdProjectFolders = true)`.
 */
abstract class KotlinMavenImportingTestBase(
  mavenVersion: String,
  modelVersion: String,
  private val createStdProjectFolders: Boolean = true,
) {
  protected val maven: MavenImportingTestFixture by mavenImportingFixture(mavenVersion = mavenVersion, modelVersion = modelVersion)

  protected val project: Project get() = maven.project

  protected val kotlinVersion: String = "1.1.3"

  private val artifactDownloadingScheduled = AtomicInteger()
  private val artifactDownloadingFinished = AtomicInteger()
  private var sdkCreationChecker: KotlinSdkCreationChecker? = null

  /** Marks a test that imports a multiplatform project; skipped while the Kotlin facet bridge is enabled. */
  protected annotation class MppGoal

  @BeforeEach
  fun setUpKotlinMavenImportingBase() {
    if (KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled) {
      Assumptions.assumeFalse(
        this.javaClass.isAnnotationPresent(MppGoal::class.java),
        "Disable MPP import tests because Workspace model does not support it yet",
      )
    }
    sdkCreationChecker = KotlinSdkCreationChecker()
    if (createStdProjectFolders) maven.createStdProjectFolders()
    project.messageBus.connect(maven.testRootDisposable)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun artifactDownloadingScheduled() {
          artifactDownloadingScheduled.incrementAndGet()
        }

        override fun artifactDownloadingFinished() {
          artifactDownloadingFinished.incrementAndGet()
        }
      })
  }

  @AfterEach
  fun tearDownKotlinMavenImportingBase(): Unit = runBlocking {
    try {
      assertWithinTimeout {
        val scheduled = artifactDownloadingScheduled.get()
        val finished = artifactDownloadingFinished.get()
        Assertions.assertEquals( scheduled, finished,"Expected $scheduled artifact downloads, but finished $finished")
      }
    }
    finally {
      resetCodeStyle(project)
      sdkCreationChecker?.removeNewKotlinSdk()
    }
  }

  protected fun facetSettings(moduleName: String): IKotlinFacetSettings =
    KotlinFacet.get(maven.getModule(moduleName))!!.configuration.settings

  protected val facetSettings: IKotlinFacetSettings
    get() = facetSettings("project")

  @OptIn(KaExperimentalApi::class)
  protected suspend fun checkStableModuleName(
    projectName: String,
    expectedName: String,
    platform: TargetPlatform,
    isProduction: Boolean,
  ): Unit = readAction {
    val module = maven.getModule(projectName)
    val kaModule = module.toKaSourceModule(if (isProduction) KaSourceModuleKind.PRODUCTION else KaSourceModuleKind.TEST)
    Assertions.assertEquals("<$expectedName>", kaModule?.stableModuleName)
  }

  protected fun assertKotlinSources(moduleName: String, vararg expectedSources: String) {
    maven.assertSourceRoots(moduleName, SourceKotlinRootType, *expectedSources)
  }

  protected fun assertDefaultKotlinResources(moduleName: String, vararg additionalSources: String) {
    maven.assertSourceRoots(moduleName, ResourceKotlinRootType, *(maven.defaultResources() + additionalSources))
  }

  protected fun assertKotlinTestSources(moduleName: String, vararg expectedSources: String) {
    maven.assertSourceRoots(moduleName, TestSourceKotlinRootType, *expectedSources)
  }

  protected fun assertDefaultKotlinTestResources(moduleName: String, vararg additionalSources: String) {
    maven.assertSourceRoots(moduleName, TestResourceKotlinRootType, *(maven.defaultTestResources() + additionalSources))
  }

  object TestVersions {
    object Kotlin {
      const val KOTLIN_2_3_10: String = "2.3.10"
      const val KOTLIN_2_3_20: String = "2.3.20-Beta2"

      const val LATEST_STABLE: String = "2.3.10"
    }
  }
}
