package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.model.internal.platformBridge.startVenvExclusion
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.delay
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class PyExcludeVenvTest {
  private val tempDirFixture = tempPathFixture()
  private val module by projectFixture().moduleFixture(tempDirFixture, addPathToSourceRoot = true)

  @Test
  fun testExclude(): Unit = timeoutRunBlocking {
    val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDirFixture.get())!!
    val dir1 = writeAction { root.createDirectory(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME) }
    startVenvExclusion(module.project)
    val dir2 = writeAction { root.createDirectory("fopp").createDirectory(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME) }

    while (module.rootManager.excludeRoots.size != 2) {
      delay(100.milliseconds)
    }
    assertThat("Wrong items excluded", module.rootManager.excludeRoots.toList(), containsInAnyOrder(dir1, dir2))
  }
}
