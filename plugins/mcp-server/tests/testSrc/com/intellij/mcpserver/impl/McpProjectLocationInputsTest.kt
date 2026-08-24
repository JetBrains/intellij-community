package com.intellij.mcpserver.impl

import com.intellij.configurationStore.FileStorageAnnotation
import com.intellij.configurationStore.ProjectStoreDescriptor
import com.intellij.configurationStore.ProjectStorePathCustomizer
import com.intellij.configurationStore.StateStorageManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.project.ProjectStoreOwner
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@TestApplication
class McpProjectLocationInputsTest {
  companion object {
    val firstProjectFixture = projectFixture(openAfterCreation = true)
    val firstProject by firstProjectFixture
    val secondProjectFixture = projectFixture(openAfterCreation = true)
    val secondProject by secondProjectFixture

    /**
     * A directory above the project directory, akin to a repository root attached to a solution opened from
     * a subdirectory of that repository: a content root of the project, but not the project directory itself.
     */
    val outerRootPathFixture = tempPathFixture()
    val outerRootProjectFixture = projectFixture(openAfterCreation = true)
    val outerRootProject by outerRootProjectFixture
    val outerRoot by outerRootProjectFixture.moduleFixture().sourceRootFixture(pathFixture = outerRootPathFixture)

    /** A project whose own directory is located under [outerRoot]. */
    val nestedProjectPathFixture = testFixture<Path>("nested project directory") {
      val path = outerRootPathFixture.init().resolve("nested-project")
      withContext(Dispatchers.IO) { path.createDirectories() }
      initialized(path) {}
    }
    val nestedProject by projectFixture(pathFixture = nestedProjectPathFixture, openAfterCreation = true)

    /**
     * A directory that is a content root of two projects at once, akin to a repository that holds two solutions. It
     * tells neither project from the other.
     */
    val sharedRootPathFixture = tempPathFixture()
    val firstSharedRootProjectFixture = projectFixture(openAfterCreation = true)
    val firstSharedRootProject by firstSharedRootProjectFixture
    val firstSharedRoot by firstSharedRootProjectFixture.moduleFixture().sourceRootFixture(pathFixture = sharedRootPathFixture)
    val secondSharedRootProjectFixture = projectFixture(openAfterCreation = true)
    val secondSharedRootProject by secondSharedRootProjectFixture
    val secondSharedRoot by secondSharedRootProjectFixture.moduleFixture().sourceRootFixture(pathFixture = sharedRootPathFixture)
  }

  @Test
  fun `projectPath argument wins over all other sources`() {
    runBlocking(Dispatchers.Default) {
      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = secondProject.basePath,
        projectPathFromCallHeader = firstProject.basePath,
        projectPathFromSessionHeader = firstProject.basePath,
        roots = setOf(projectRootUri(firstProject)),
      )

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `invalid projectPath argument throws without fallback`() {
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        service<McpSessionProjectResolver>().resolveSessionProject(
          projectPathFromArgument = "/tmp/not-an-open-project",
          projectPathFromCallHeader = firstProject.basePath,
          projectPathFromSessionHeader = secondProject.basePath,
          roots = setOf(projectRootUri(secondProject)),
        )
      }
    }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("`${projectPathParameterName}`=`/tmp/not-an-open-project` doesn't correspond to any open project.")
  }

  @Test
  fun `call header wins over session header`() {
    runBlocking(Dispatchers.Default) {
      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = null,
        projectPathFromCallHeader = secondProject.basePath,
        projectPathFromSessionHeader = firstProject.basePath,
        roots = setOf(projectRootUri(firstProject)),
      )

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `session header wins over roots`() {
    runBlocking(Dispatchers.Default) {
      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = null,
        projectPathFromCallHeader = "/tmp/not-an-open-project",
        projectPathFromSessionHeader = secondProject.basePath,
        roots = setOf(projectRootUri(firstProject)),
      )

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `roots are used as final fallback`() {
    runBlocking(Dispatchers.Default) {
      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = null,
        projectPathFromCallHeader = "/tmp/not-an-open-project",
        projectPathFromSessionHeader = "/tmp/also-not-an-open-project",
        roots = setOf(projectRootUri(secondProject)),
      )

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `bazel project root path resolves project opened by bazelproject identity`(
    @TempDir tempDir: Path,
    @TestDisposable disposable: Disposable,
  ) {
    val repo = tempDir.resolve("repo")
    val projectIdentityFile = createBazelProjectIdentityFile(repo)
    ExtensionTestUtil.maskExtensions(
      PROJECT_STORE_PATH_CUSTOMIZER_EP,
      listOf(BazelLikeProjectStorePathCustomizer()),
      disposable,
      fireEvents = false,
    )

    val bazelProject = runBlocking(Dispatchers.Default) {
      ProjectManagerEx.getInstanceEx().openProjectAsync(
        projectIdentityFile = projectIdentityFile,
        options = OpenProjectTask {
          projectRootDir = repo
          createModule = false
          runConfigurators = false
        },
      )
    }
    assertThat(bazelProject).isNotNull()

    try {
      val store = (bazelProject!! as ProjectStoreOwner).componentStore
      assertThat(store.storeDescriptor.presentableUrl).isEqualTo(projectIdentityFile)
      assertThat(store.projectBasePath).isEqualTo(repo)
      assertThat(bazelProject.basePath).isEqualTo(repo.invariantSeparatorsPathString)

      runBlocking(Dispatchers.Default) {
        val project = service<McpSessionProjectResolver>().resolveSessionProject(
          projectPathFromArgument = null,
          projectPathFromCallHeader = repo.invariantSeparatorsPathString,
          projectPathFromSessionHeader = null,
          roots = emptySet(),
        )

        assertThat(project).isSameAs(bazelProject)
      }
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(bazelProject!!)
    }
  }

  @Test
  fun `chaining mode throws user-facing error without internal source details`() {
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        service<McpSessionProjectResolver>().resolveSessionProject(
          projectPathFromArgument = null,
          projectPathFromCallHeader = "/tmp/not-an-open-project",
          projectPathFromSessionHeader = "/tmp/also-not-an-open-project",
          roots = setOf("file:///tmp/missing-root"),
        )
      }
    }
      .isInstanceOf(McpExpectedError::class.java)
      .satisfies({ throwable: Throwable ->
        val error = throwable as McpExpectedError
        assertThat(error.mcpErrorText).contains("You may specify the project path via `${projectPathParameterName}` parameter")
        assertThat(error.mcpErrorText).doesNotContain("header")
        assertThat(error.mcpErrorText).doesNotContain("env")
        assertThat(error.mcpErrorText).doesNotContain(IJ_MCP_SERVER_PROJECT_PATH)
      })
  }

  @Test
  fun `projectPath argument pointing at a content root outside the project directory resolves that project`() {
    runBlocking(Dispatchers.Default) {
      val outerRootPath = outerRoot.virtualFile.toNioPath()
      awaitBaseDirectory(outerRootProject, outerRootPath)

      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = outerRootPath.invariantSeparatorsPathString,
        projectPathFromCallHeader = null,
        projectPathFromSessionHeader = null,
        roots = emptySet(),
      )

      assertThat(project).isSameAs(outerRootProject)
    }
  }

  @Test
  fun `roots pointing at a content root outside the project directory resolve that project`() {
    runBlocking(Dispatchers.Default) {
      val outerRootPath = outerRoot.virtualFile.toNioPath()
      awaitBaseDirectory(outerRootProject, outerRootPath)

      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = null,
        projectPathFromCallHeader = null,
        projectPathFromSessionHeader = null,
        roots = setOf(outerRootPath.toUri().toString()),
      )

      assertThat(project).isSameAs(outerRootProject)
    }
  }

  @Test
  fun `innermost project wins over a project matched by its outer content root`() {
    runBlocking(Dispatchers.Default) {
      val outerRootPath = outerRoot.virtualFile.toNioPath()
      awaitBaseDirectory(outerRootProject, outerRootPath)
      val nestedProjectPath = Path.of(nestedProject.basePath!!)
      assertThat(nestedProjectPath).startsWith(outerRootPath)

      val project = service<McpSessionProjectResolver>().resolveSessionProject(
        projectPathFromArgument = nestedProjectPath.invariantSeparatorsPathString,
        projectPathFromCallHeader = null,
        projectPathFromSessionHeader = null,
        roots = emptySet(),
      )

      assertThat(project).isSameAs(nestedProject)
    }
  }

  @Test
  fun `a content root shared by two projects resolves no project`() {
    runBlocking(Dispatchers.Default) {
      val sharedRootPath = firstSharedRoot.virtualFile.toNioPath()
      assertThat(secondSharedRoot.virtualFile.toNioPath()).isEqualTo(sharedRootPath)
      awaitBaseDirectory(firstSharedRootProject, sharedRootPath)
      awaitBaseDirectory(secondSharedRootProject, sharedRootPath)

      assertThatThrownBy {
        runBlocking(Dispatchers.Default) {
          service<McpSessionProjectResolver>().resolveSessionProject(
            projectPathFromArgument = sharedRootPath.invariantSeparatorsPathString,
            projectPathFromCallHeader = null,
            projectPathFromSessionHeader = null,
            roots = emptySet(),
          )
        }
      }
        .isInstanceOf(McpExpectedError::class.java)
        .hasMessageContaining("doesn't correspond to any open project.")
    }
  }

  /**
   * [com.intellij.openapi.project.BaseProjectDirectories] picks workspace model changes up asynchronously.
   */
  private suspend fun awaitBaseDirectory(project: Project, directory: Path) {
    val normalizedDirectory = directory.normalize()
    withTimeout(1.minutes) {
      while (project.getBaseDirectories().none { it.toNioPathOrNull()?.normalize() == normalizedDirectory }) {
        delay(50.milliseconds)
      }
    }
  }

  private fun projectRootUri(project: Project): String = Path.of(project.basePath!!).toUri().toString()
}

private fun createBazelProjectIdentityFile(repo: Path): Path {
  Files.createDirectories(repo.resolve(Project.DIRECTORY_STORE_FOLDER))
  Files.createFile(repo.resolve("MODULE.bazel"))
  val toolbox = repo.resolve("toolbox")
  Files.createDirectories(toolbox)
  return Files.createFile(toolbox.resolve("toolbox.bazelproject"))
}

private val PROJECT_STORE_PATH_CUSTOMIZER_EP =
  ExtensionPointName.create<ProjectStorePathCustomizer>("com.intellij.projectStorePathCustomizer")

private class BazelLikeProjectStorePathCustomizer : ProjectStorePathCustomizer {
  override fun getStoreDirectoryPath(projectRoot: Path): ProjectStoreDescriptor? {
    if (!projectRoot.fileName.toString().endsWith(".bazelproject")) return null
    val bazelRoot = findBazelRoot(projectRoot) ?: projectRoot.parent
    return BazelLikeProjectStoreDescriptor(
      projectIdentityFile = projectRoot,
      dotIdea = bazelRoot.resolve(Project.DIRECTORY_STORE_FOLDER),
      historicalProjectBasePath = bazelRoot,
    )
  }

  private fun findBazelRoot(path: Path): Path? {
    var directory: Path? = path.parent
    while (directory != null) {
      if (Files.isRegularFile(directory.resolve("MODULE.bazel"))) {
        return directory
      }
      directory = directory.parent
    }
    return null
  }
}

private class BazelLikeProjectStoreDescriptor(
  override val projectIdentityFile: Path,
  override val dotIdea: Path,
  override val historicalProjectBasePath: Path,
) : ProjectStoreDescriptor {
  override fun testStoreDirectoryExistsForProjectRoot(): Boolean = Files.isRegularFile(projectIdentityFile)

  override fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage =
    FileStorageAnnotation.PROJECT_FILE_STORAGE_ANNOTATION

  override fun getModuleStorageSpecs(
    component: PersistentStateComponent<*>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
    project: Project,
  ): List<Storage> = listOf(FileStorageAnnotation.MODULE_FILE_STORAGE_ANNOTATION)

  override fun <T : Any> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
  ): List<Storage> = listOf(FileStorageAnnotation.PROJECT_FILE_STORAGE_ANNOTATION)
}
