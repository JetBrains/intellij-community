package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.modules
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.intellij.python.pyproject.model.internal.MODEL_REBUILD
import com.intellij.python.pyproject.model.internal.platformBridge.rebuildPyProjectModelForTest
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.io.write
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.apache.tuweni.toml.TomlTable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes


@Timeout(TIMEOUT_MIN.toLong(), unit = TimeUnit.MINUTES)
@TestApplication
class PyProjectModelSyncServiceTest {
  private val members = arrayOf("foo", "bar").sortedArray()

  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  @BeforeEach
  fun prepareWorkspace(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    for (member in members) {
      root.resolve(member).resolve(PY_PROJECT_TOML).write("""
      [project]
      name = "$member"
     """.trimIndent())
    }
  }

  @Test
  fun testBuildProjectRainyDay(): Unit = timeoutRunBlocking {
    pathFixture.get().deleteRecursively()
    rebuildPyProjectModelForTest(projectFixture.get())
  }

  @ParameterizedTest
  @ValueSource(strings = ["foo", "unused"])
  @TestFor(issues = ["PY-91841"])
  fun testStaleRegistrationDoesNotRepeatSourceRootDiscovery(
    staleName: String,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(TIMEOUT_MIN.minutes) {
    val project = projectFixture.get()
    project.workspaceModel.update("Add a stale module registration") { storage ->
      storage.addEntity(ModuleEntity(staleName, emptyList(), NonPersistentEntitySource))
    }
    val managers = PyProjectManager.EP.extensionList
    val manager = managers.first()
    val inspectedRoots = CopyOnWriteArrayList<Directory>()
    val countingManager = object : PyProjectManager by manager {
      override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> {
        inspectedRoots.add(projectRoot)
        return manager.getSrcRoots(toml, projectRoot)
      }
    }
    ExtensionTestUtil.maskExtensions(PyProjectManager.EP, listOf(countingManager) + managers.drop(1), disposable)

    rebuildPyProjectModelForTest(project)

    Assertions.assertEquals(members.map { pathFixture.get().resolve(it) }.sorted(), inspectedRoots.sorted())
    Assertions.assertEquals(members.toList(), project.modules.filter { it.isPyProjectTomlBased }.map { it.name }.sorted())
  }

  @Test
  fun testFindTomlFilesInIndex(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    // The rebuild refreshes the temp tree into the VFS, which the filename index needs.
    rebuildPyProjectModelForTest(project)
    val root = pathFixture.get()
    val found = findPyProjectTomlFilesInIndex(roots = setOf(root), excludedPaths = emptySet())
    Assertions.assertEquals(
      members.map { root.resolve(it).resolve(PY_PROJECT_TOML) }.sorted(),
      found.sorted(),
      "Wrong toml files found in the index",
    )
  }

  @Test
  fun testBuildProjectSunnyDay(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(TIMEOUT_MIN.minutes) {
    launch {
      rebuildPyProjectModelForTest(projectFixture.get())
    }

    val m = Mutex(locked = true)
    projectFixture.get().messageBus.connect(disposable).subscribe(MODEL_REBUILD, ModelRebuiltListener { project ->
      try {
        for (module in project.modules) {
          Assertions.assertTrue(module.isPyProjectTomlBased, "$module isn't pyproject based")
        }
        val moduleNames = project.modules.map { it.name }.sorted().toTypedArray()
        Assertions.assertArrayEquals(members, moduleNames, "Wrong modules created")
      }
      finally {
        m.unlock()
      }
    })
    m.lock()
  }
}

private const val TIMEOUT_MIN = 2
