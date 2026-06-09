// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.maven.testFramework.MavenWrapperTestFixture
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.dom.inspections.MavenModelInspection
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenEmbedderWrappers
import org.jetbrains.idea.maven.project.MavenPluginResolver
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.project.PluginResolutionResult
import org.jetbrains.idea.maven.server.MavenServerManager
import com.intellij.platform.util.progress.RawProgressReporter
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

/**
 * JUnit 5 test fixture that hosts a [CodeInsightTestFixture] over a real Maven project, providing the
 * completion / reference-resolution / highlighting helpers that the legacy `MavenDomWithIndicesTestCase`
 * hierarchy used to offer, without extending it.
 *
 * Obtain it via [mavenDomFixture] using the property-delegate pattern:
 * ```
 * @TestApplication
 * class MyMavenCompletionTest {
 *   private val maven by mavenDomFixture(withIndices = true)
 *
 *   @Test
 *   fun test() = runBlocking {
 *     maven.updateProjectPom("<groupId>test</groupId><artifactId>project</artifactId><version>1</version>")
 *     maven.assertCompletionVariants(maven.projectPom, "...")
 *   }
 * }
 * ```
 *
 * This class is intentionally thin: it owns the per-test state and lifecycle only. The completion,
 * reference-resolution, highlighting, rename, import, versioning and module helpers are provided as
 * extension functions grouped by concern in sibling `MavenDomFixture*.kt` files (same package), so call
 * sites stay `maven.foo(...)` while each concern lives in its own small file.
 *
 * It is created by [mavenDomFixture] over a per-method [Project] supplied by the platform `projectFixture`, with the
 * [CodeInsightTestFixture] attached (set up) **before** the initial Maven import (see [attachCodeInsight]) so that its
 * `VirtualFilePointerTracker` baseline is established before the import creates project-scoped pointers. A fresh project
 * is created and disposed for every test method.
 *
 * When [indices] is non-null, a [MavenIndicesTestFixture] copies the local and extra test repositories
 * into a temp dir, points Maven's local repository at it, and builds GAV indices so that completion and
 * resolution of repository artifacts work offline.
 */
class MavenDomTestFixture internal constructor(
  val project: Project,
  val dir: Path,
  internal val mavenVersion: String = "bundled",
  internal val modelVersion: String = MavenConstants.MODEL_VERSION_4_0_0,
  private val skipPluginResolution: Boolean = true,
  internal val indices: MavenDomTestFixtureIndices? = null,
) {
  internal lateinit var disposable: Disposable
  private lateinit var jdkFixture: MavenProjectJDKTestFixture
  private var wrapperFixture: MavenWrapperTestFixture? = null
  private var originalAutoCompletion = false
  internal val configTimestamps: MutableMap<VirtualFile, Long> = HashMap()

  /**
   * The hosted code-insight fixture. Attached (set up) before the initial Maven import via [attachCodeInsight] so that
   * its `VirtualFilePointerTracker` baseline is established before the import creates project-scoped pointers.
   */
  lateinit var fixture: CodeInsightTestFixture
    private set

  var indicesFixture: MavenIndicesTestFixture? = null
    private set

  internal var myProjectPom: VirtualFile? = null

  val projectsManager: MavenProjectsManager
    get() = MavenProjectsManager.getInstance(project)

  val projectRoot: VirtualFile
    get() = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(project.basePath!!))!!

  val projectPom: VirtualFile
    get() = myProjectPom!!

  val repositoryHelper
    get() = indicesFixture!!.repositoryHelper

  var repositoryPath: Path
    get() = MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo()
    set(path) {
      projectsManager.generalSettings.setLocalRepository(path.toCanonicalPath())
      MavenSettingsCache.getInstance(project).reload()
    }

  @Suppress("PropertyName")
  val RENDERING_TEXT: Function<LookupElement, String?> = Function { li: LookupElement ->
    val presentation = LookupElementPresentation()
    li.renderElement(presentation)
    presentation.itemText
  }

  @Suppress("PropertyName")
  val LOOKUP_STRING: Function<LookupElement, String?> = Function { obj: LookupElement -> obj.lookupString }

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

    // Register the JDK (so the Maven server can start) but do NOT leave it as the project SDK. Legacy DOM tests ran
    // without a project SDK (MavenProjectJDKTestFixture was opt-in via withRealJDK), and the "create module" quickfix
    // emits <maven.compiler.*> properties only when a Java project SDK is present. Clearing it keeps that parity so
    // golden poms match; the Maven importer then falls back to the internal (real) JDK, as it did in legacy.
    edtWriteAction { ProjectRootManager.getInstance(project).projectSdk = null }

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

  /** Matcher used by [checkHighlighting] overloads that take expected highlights. */
  class Highlight(
    val severity: HighlightSeverity = HighlightSeverity.ERROR,
    val text: String? = null,
    val description: String? = null,
  ) {
    fun matches(info: HighlightInfo): Boolean {
      return severity == info.severity &&
             (text == null || text == info.text) &&
             (description == null || description == info.description)
    }

    override fun toString(): String = "Highlight(severity=$severity, text=$text, description=$description)"
  }

  internal object NoOpPluginResolver : MavenPluginResolver {
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

    @Language("XML")
    const val DEFAULT_POM: String = """
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
    """

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

data class MavenDomTestFixtureIndices(val localRepoDir: String, val extraRepoDirs: List<String>)