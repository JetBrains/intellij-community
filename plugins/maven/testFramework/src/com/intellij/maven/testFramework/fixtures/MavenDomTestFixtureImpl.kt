// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.maven.testFramework.MavenWrapperTestFixture
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.dom.inspections.MavenModelInspection
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenEmbedderWrappers
import org.jetbrains.idea.maven.project.MavenPluginResolver
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.project.PluginResolutionResult
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Files
import java.nio.file.Path

internal class MavenDomTestFixtureImpl internal constructor(
  override val project: Project,
  override val dir: Path,
  override val mavenVersion: String = "bundled",
  override val modelVersion: String = MavenConstants.MODEL_VERSION_4_0_0,
  private val skipPluginResolution: Boolean = true,
  override val indices: MavenDomTestFixtureIndices? = null,
) : MavenDomTestFixture {
  override lateinit var disposable: Disposable
  private lateinit var jdkFixture: MavenProjectJDKTestFixture
  private var wrapperFixture: MavenWrapperTestFixture? = null
  private var originalAutoCompletion = false
  override val configTimestamps: MutableMap<VirtualFile, Long> = HashMap()

  /**
   * The hosted code-insight fixture. Attached (set up) before the initial Maven import via [attachCodeInsight] so that
   * its `VirtualFilePointerTracker` baseline is established before the import creates project-scoped pointers.
   */
  override lateinit var fixture: CodeInsightTestFixture
    private set

  var indicesFixture: MavenIndicesTestFixture? = null
    private set

  internal var myProjectPom: VirtualFile? = null

  override val projectsManager: MavenProjectsManager
    get() = MavenProjectsManager.getInstance(project)

  override var projectPom: VirtualFile
    get() = myProjectPom!!
    set(value) {
      myProjectPom = value
    }

  override val repositoryHelper
    get() = indicesFixture!!.repositoryHelper

  override var repositoryPath: Path
    get() = MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo()
    set(path) {
      projectsManager.generalSettings.setLocalRepository(path.toCanonicalPath())
      MavenSettingsCache.getInstance(project).reload()
    }

  /**
   * Attaches the hosted code-insight fixture. Invoked by [mavenDomFixture] BEFORE [setUp]'s import, so the fixture's
   * `VirtualFilePointerTracker` baseline is taken before the Maven import creates project-scoped pointers.
   */
  internal suspend fun attachCodeInsight(codeInsight: CodeInsightTestFixture) {
    fixture = codeInsight
    // org.jetbrains.idea.maven.utils.MavenRehighlighter
    (fixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    edtWriteAction {
      fixture.enableInspections(MavenModelInspection::class.java, XmlUnresolvedReferenceInspection::class.java)
    }
    originalAutoCompletion = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
  }

  internal suspend fun setUp(@Language(value = "XML", prefix = "<project>", suffix = "</project>") initialPom: String?) {
    disposable = Disposer.newDisposable("MavenDomTestFixture")

    // The Maven server process needs a real JDK to start.
    VfsRootAccess.allowRootAccess(disposable, IdeaTestUtil.requireRealJdkHome())
    jdkFixture = MavenProjectJDKTestFixture(project, JDK_NAME)
    edtWriteAction { jdkFixture.setUp() }

    // Plugin resolution is slow and hits the network; skip it for offline sync unless a test opts in.
    if (skipPluginResolution) {
      project.replaceService(MavenPluginResolver::class.java, NoOpPluginResolver, disposable)
    }

    // Run the import against the requested Maven distribution: embedded for "bundled", a downloaded wrapper otherwise.
    if (mavenVersion != "bundled") {
      wrapperFixture = MavenWrapperTestFixture(project, MavenTestVersions.getActualVersion(mavenVersion))
      wrapperFixture!!.setUp()
    }

    // Always give Maven a user settings file pointing at the JetBrains cache redirector: it keeps Maven Central out of
    // the loop and, crucially, lets Maven 4 resolve its core extensions/plugins (without a settings file the Maven 4
    // sync hangs). The indices fixture additionally overrides the local repository with the test data below.
    val settingsXml = Path.of(project.basePath!!).resolve("settings.xml")
    Files.createDirectories(settingsXml.parent)
    Files.writeString(settingsXml, CACHE_REDIRECTOR_SETTINGS)
    projectsManager.generalSettings.setUserSettingsFile(settingsXml.toString())
    MavenSettingsCache.getInstance(project).reload()

    if (null != indices) {
      indicesFixture = MavenIndicesTestFixture(dir, project, disposable, indices.localRepoDir, *indices.extraRepoDirs.toTypedArray())
      indicesFixture!!.setUpBeforeImport()
    }

    projectsManager.initForTests()

    if (initialPom != null) {
      importProjectAsync(initialPom)
    }
    else {
      MavenSettingsCache.getInstance(project).reloadAsync()
    }

    if (null != indices) {
      withContext(Dispatchers.EDT) { indicesFixture!!.setUpAfterImport() }
    }
  }

  internal suspend fun tearDown() {
    // The hosted code-insight fixture is composed separately (see [mavenDomFixture]); the JUnit5 fixture framework tears it
    // down — which disposes the project and asserts VirtualFilePointer disposal — after this Maven tear-down has run.
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = originalAutoCompletion
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    configTimestamps.clear()
    runCatching { indicesFixture?.tearDown() }
    runCatching { wrapperFixture?.tearDown() }
    // Shut down the Maven server process so the next test method/Maven version starts from a clean connector state.
    runCatching { MavenServerManager.getInstance().closeAllConnectorsAndWait() }
    if (::jdkFixture.isInitialized) {
      runCatching { edtWriteAction { jdkFixture.tearDown() } }
    }
    runCatching { if (::disposable.isInitialized) Disposer.dispose(disposable) }
  }

  private object NoOpPluginResolver : MavenPluginResolver {
    override suspend fun resolvePlugins(
      mavenProjects: Collection<MavenProject>,
      forceUpdateSnapshots: Boolean,
      mavenEmbedderWrappers: MavenEmbedderWrappers,
      process: RawProgressReporter,
      eventHandler: MavenEventHandler,
    ): PluginResolutionResult = PluginResolutionResult(emptySet())
  }

  companion object {
    private const val JDK_NAME = "Maven Test JDK"

    private val CACHE_REDIRECTOR_SETTINGS = """
      <settings>
        <mirrors>
          <mirror>
            <id>central-mirror</id>
            <url>https://cache-redirector.jetbrains.com/repo1.maven.org/maven2</url>
            <mirrorOf>central</mirrorOf>
          </mirror>
        </mirrors>
      </settings>
    """.trimIndent()
  }
}