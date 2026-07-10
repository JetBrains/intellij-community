package com.intellij.python.ty.typeEngine

import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.python.lsp.core.PyLspCoreBundle
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.ty.TyBundle
import com.intellij.python.ty.TyLspIntegrationProvider
import com.intellij.python.ty.TyService
import com.intellij.python.ty.TyUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class TyPyTypeEngine : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.TY

  override fun isSupported(project: Project): Boolean {
    return isTyTypeEngineFeatureEnabled(project)
  }

  override fun updateLspServers(project: Project) {
    val lspServerManager = LspClientManager.getInstance(project)
    if (shouldBeEnabled(project)) {
      lspServerManager.stopAndRestartClientsIfNeeded<TyLspIntegrationProvider>()
    }
    else {
      lspServerManager.stopClients<TyLspIntegrationProvider>()
    }
  }

  private fun shouldBeEnabled(project: Project): Boolean {
    return isSupported(project) && project.service<PyTypeEngineProjectSettings>().typeEngine == PyTypeEngineType.TY
  }


  @Suppress("DialogTitleCapitalization")
  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange {
    return rowsRange {
      val tyInstalledProperty = propertyGraph.property(TyUtil.isTyInstalled())

      row {
        comment(PyLspCoreBundle.message("ty.description"))
      }

      row {
        button(TyBundle.message("install.ty.button")) {
          project.service<TyService>().cs.launch {
            withBackgroundProgress(project, TyBundle.message("install.ty.progress"), true) {
              val result = coroutineScope {
                createProgressPipe().collectProgressUpdates {
                  coroutineToIndicator { indicator ->
                    TyUtil.downloadTyBinary(indicator)
                  }
                }
              }
              if (result != null) {
                // Update the property to reflect ty is now installed
                tyInstalledProperty.set(true)
                Messages.showInfoMessage(
                  project,
                  TyBundle.message("install.ty.success", result.toString()),
                  TyBundle.message("install.ty.title")
                )
              }
              else {
                Messages.showErrorDialog(
                  project,
                  TyBundle.message("install.ty.failed"),
                  TyBundle.message("install.ty.title")
                )
              }
            }
          }
        }
      }
    }
  }
}
