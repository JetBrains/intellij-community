package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.platformBridge.refreshProjectRootsIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.io.createDirectories
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.DEFAULT_VIRTUALENV_DIRNAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests `findPyProjectTomlFilesInIndex`, the only `pyproject.toml` search (PY-91841).
 *
 * One rule has no cheap coverage here. This test fakes a virtualenv with an empty `bin/python`, and
 * `PyWalkFileSystemEnvTest` builds a real one with a real interpreter.
 */
@TestApplication
internal class PyIndexSearchTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  private lateinit var root: Path
  private lateinit var excludedDir: Path
  private lateinit var expectedTomlFiles: List<Path>
  private var linkCreated = false

  @BeforeEach
  fun createStructure(): Unit = timeoutRunBlocking {
    root = pathFixture.get()
    excludedDir = root.resolve("excluded").createDirectory()
    expectedTomlFiles = listOf(
      root.resolve(PY_PROJECT_TOML).createFile(),
      root.resolve("dir").createDirectory().resolve(PY_PROJECT_TOML).createFile(),
      excludedDir.resolve(PY_PROJECT_TOML).createFile(),
    )

    // None of the following may be reported.
    // A dot directory.
    root.resolve(".abc").createDirectories().resolve(PY_PROJECT_TOML).createFile()
    root.resolve(DEFAULT_VIRTUALENV_DIRNAME).createDirectory().resolve(PY_PROJECT_TOML).createFile()
    // A virtualenv whose name has no dot, recognized by the python in it.
    root.resolve("venv").createDirectory().apply {
      resolve("Scripts").createDirectories().resolve("python.exe").createFile()
      resolve("bin").createDirectories().resolve("python").createFile()
      resolve(PY_PROJECT_TOML).createFile()
    }
    // A well-known heavy directory, pruned by name.
    root.resolve("node_modules").createDirectory().resolve(PY_PROJECT_TOML).createFile()
    root.resolve("lib").resolve("site-packages").createDirectories().resolve(PY_PROJECT_TOML).createFile()
    // A directory that carries the name of the file must never become a module.
    root.resolve("dirLikeFile").resolve(PY_PROJECT_TOML).createDirectories()
    // A symbolic link to a directory that holds a `pyproject.toml`. The VFS follows a link and the walk does
    // not, so the link must not add a second module for the same file.
    linkCreated = try {
      Files.createSymbolicLink(root.resolve("link"), root.resolve("dir"))
      true
    }
    catch (_: IOException) {
      false // Windows refuses without the privilege.
    }

    refreshProjectRootsIntoVfs(projectFixture.get())
  }

  @Test
  fun testSunnyDay(): Unit = timeoutRunBlocking {
    val files = findPyProjectTomlFilesInIndex(setOf(root), excludedPaths = emptySet())
    assertThat(files).containsExactlyInAnyOrderElementsOf(expectedTomlFiles)
  }

  @Test
  fun testExcludedPaths(): Unit = timeoutRunBlocking {
    val files = findPyProjectTomlFilesInIndex(setOf(root), excludedPaths = setOf(excludedDir))
    assertThat(files)
      .describedAs("pyproject.toml inside an excluded folder must not be reported")
      .containsExactlyInAnyOrderElementsOf(expectedTomlFiles.filterNot { it.startsWith(excludedDir) })
  }

  @Test
  fun testFileOutsideEveryRootIsIgnored(): Unit = timeoutRunBlocking {
    val files = findPyProjectTomlFilesInIndex(setOf(root.resolve("dir")), excludedPaths = emptySet())
    assertThat(files)
      .describedAs("The name enumerator is application wide, so only a file under a root may be reported")
      .containsExactly(root.resolve("dir").resolve(PY_PROJECT_TOML))
  }

  @Test
  fun testNoRootsReportsNothing(): Unit = timeoutRunBlocking {
    assertThat(findPyProjectTomlFilesInIndex(emptySet(), emptySet())).isEmpty()
  }

  /**
   * A root of a dot name gets no model, and the three rules of PY-91841 agree on it. `loadSubtreesIntoVfs`
   * prunes such a directory, `EventFilter` drops an event under it, and this filter rejects its files.
   *
   * The case puts the file in the VFS itself, so the index reports it and only the filter can reject it.
   */
  @Test
  fun testARootOfADotNameReportsNothing(): Unit = timeoutRunBlocking {
    val dotRoot = root.resolve(".hidden-project")
    val toml = dotRoot.resolve("member").createDirectories().resolve(PY_PROJECT_TOML).createFile()
    assertThat(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(toml))
      .describedAs("the case needs the file in the VFS, or it proves nothing")
      .isNotNull()

    assertThat(findPyProjectTomlFilesInIndex(setOf(dotRoot), excludedPaths = emptySet()))
      .describedAs("a root of a dot name reports nothing")
      .isEmpty()
  }

  /**
   * A VFS event arrives for the parent directory only, so a new directory has no loaded children.
   * `loadSubtreesIntoVfs` is what makes its `pyproject.toml` visible to the filename index.
   */
  @Test
  fun testSubtreeLoadFindsNestedFile(): Unit = timeoutRunBlocking {
    val nested = root.resolve("fresh").resolve("member")
    nested.createDirectories().resolve(PY_PROJECT_TOML).createFile()

    // Refresh the root without recursion. The VFS learns about `fresh`, but not about its content.
    val rootFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root)!!
    rootFile.refresh(false, false)
    val freshFile: VirtualFile = rootFile.findChild("fresh")!!

    loadSubtreesIntoVfs(setOf(freshFile))

    val files = findPyProjectTomlFilesInIndex(setOf(root), excludedPaths = emptySet())
    assertThat(files).contains(nested.resolve(PY_PROJECT_TOML))
  }

  /**
   * The VFS follows a symbolic link and `Files.walkFileTree` does not. A link into a build cache can hold a
   * second copy of a whole project, and the search would then report every file of it twice (PY-91841).
   */
  @Test
  fun testSymbolicLinkIsIgnored(): Unit = timeoutRunBlocking {
    Assumptions.assumeTrue(linkCreated, "The file system refused to create a symbolic link")
    val files = findPyProjectTomlFilesInIndex(setOf(root), excludedPaths = emptySet())
    assertThat(files)
      .describedAs("A file behind a symbolic link must not be reported a second time")
      .doesNotContain(root.resolve("link").resolve(PY_PROJECT_TOML))
    assertThat(files).contains(root.resolve("dir").resolve(PY_PROJECT_TOML))
  }

  @Test
  fun testDirectoryNamedLikeTheFileIsIgnored(): Unit = timeoutRunBlocking {
    val files = findPyProjectTomlFilesInIndex(setOf(root), excludedPaths = emptySet())
    assertThat(files).doesNotContain(root.resolve("dirLikeFile").resolve(PY_PROJECT_TOML))
  }
}
