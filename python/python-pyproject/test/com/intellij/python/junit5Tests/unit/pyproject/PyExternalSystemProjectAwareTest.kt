package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.modules
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.internal.MODEL_REBUILD
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyExternalSystemProjectAware
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes


@Timeout(TIMEOUT_MIN.toLong(), unit = TimeUnit.MINUTES)
@TestApplication
class PyExternalSystemProjectAwareTest {
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
    val sut = PyExternalSystemProjectAware.create(projectFixture.get())
    pathFixture.get().deleteRecursively()
    sut.reloadProjectImpl()
  }

  @Test
  fun testBuildProjectSunnyDay(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(TIMEOUT_MIN.minutes) {
    val sut = PyExternalSystemProjectAware.create(projectFixture.get())
    val files = withContext(Dispatchers.IO) {
      sut.settingsFiles
    }
    Assertions.assertEquals(members.size, files.size, "Wrong number of toml files")

    launch {
      sut.reloadProjectImpl()
    }

    val m = Mutex(locked = true)
    projectFixture.get().messageBus.connect(disposable).subscribe(MODEL_REBUILD, ModelRebuiltListener { project ->
      try {
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