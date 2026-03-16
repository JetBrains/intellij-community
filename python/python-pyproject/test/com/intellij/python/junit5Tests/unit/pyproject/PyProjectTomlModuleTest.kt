package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyExternalSystemProjectAware
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createFile
import com.jetbrains.python.PyNames
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
internal class PyProjectTomlModuleTest {
  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture = tempDirFixture)


  @Test
  fun pyProjectModuleTest(): Unit = timeoutRunBlocking {
    var module = edtWriteAction {
      ModuleManager.getInstance(projectFixture.get()).newNonPersistentModule("myModule", PyNames.PYTHON_MODULE_ID)
    }
    val externalSystemAware = PyExternalSystemProjectAware.create(projectFixture.get())
    val dir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDirFixture.get())!!

    externalSystemAware.reloadProjectImpl()
    Assertions.assertFalse(module.isPyProjectTomlBased, "Module shouldn't be pyproject based")
    module = projectFixture.get().modules[0]
    writeAction {
      val m = ModuleRootManager.getInstance(module).modifiableModel
      m.addContentEntry(dir)
      m.commit()
      dir.createFile(PY_PROJECT_TOML).writeText("[]")
    }
    externalSystemAware.reloadProjectImpl()
    module = projectFixture.get().modules[0]
    Assertions.assertTrue(module.isPyProjectTomlBased, "Module should be pyproject based")
  }
}
