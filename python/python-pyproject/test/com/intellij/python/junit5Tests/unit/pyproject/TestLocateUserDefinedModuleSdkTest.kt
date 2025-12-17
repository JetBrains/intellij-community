package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.python.junit5Tests.framework.env.pyMockSdkFixture
import com.intellij.python.pyproject.model.internal.workspaceBridge.PyProjectTomlEntitySource
import com.intellij.python.pyproject.model.internal.workspaceBridge.relocateUserDefinedModuleSdk
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.jetbrains.python.PyNames
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
class TestLocateUserDefinedModuleSdkTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture()
  private val sdkFixture = projectFixture.pyMockSdkFixture(tempPathFixture)

  /**
   * Ensure we can recreate a module and preserve its SDK, thanks to [relocateUserDefinedModuleSdk]
   */
  @Test
  fun testRelocateSdk(@TempDir path: Path): Unit = runBlocking {
    // Can't use module fixture, because we remove it manually
    val module = writeAction { ModuleManager.getInstance(projectFixture.get()).newModule(path, PyNames.PYTHON_MODULE_ID) }
    val moduleName = module.name
    val sdk = sdkFixture.get()
    writeAction {
      val m = ModuleRootManager.getInstance(module).modifiableModel
      m.sdk = sdk
      m.commit()
    }

    val workspaceModel = projectFixture.get().workspaceModel
    workspaceModel.update("...") { storage ->
      val moduleEntry = module.findModuleEntity(storage)!!
      storage.modifyModuleEntity(moduleEntry) {
        // We only transfer SDK for modules with this source
        entitySource = PyProjectTomlEntitySource(path.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager()))
      }
      relocateUserDefinedModuleSdk(storage) {
        val source = moduleEntry.entitySource
        storage.removeEntity(moduleEntry)
        storage.addEntity(ModuleEntity(moduleName, listOf(), source))
      }
    }

    val newModules = ModuleManager.getInstance(projectFixture.get()).modules
    assert(newModules.size == 1)
    val newModule = newModules[0]
    assert(newModule != module)
    Assertions.assertEquals(sdk, ModuleRootManager.getInstance(newModule).sdk, "Same SDK should be set")
  }
}
