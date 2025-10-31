package com.intellij.python.sdkConfigurator.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle.message
import com.intellij.python.sdkConfigurator.frontend.components.ModuleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import javax.swing.JComponent

internal suspend fun askUser(project: Project, modules: ModulesDTO, onResult: (Set<ModuleName>) -> Unit) {
  val viewModel = withContext(Dispatchers.Default) {
    ModulesViewModel(modules)
  }
  withContext(Dispatchers.EDT) {
    val myDialog = MyDialog(project, viewModel)
    if (myDialog.showAndGet()) {
      onResult(viewModel.checked)
    }
  }
}

private class MyDialog(project: Project, private val viewModel: ModulesViewModel) : DialogWrapper(project) {

  init {
    title = message("python.sdk.configurator.frontend.choose.modules.title")
    isResizable = false
    init()
  }

  @OptIn(ExperimentalJewelApi::class)
  override fun createCenterPanel(): JComponent {
    enableNewSwingCompositing()
    return compose(focusOnClickInside = true, content = {
      ModuleList(viewModel.checkBoxItems,
                 viewModel.icons,
                 viewModel.checked,
                 viewModel::clicked,
                 topLabel = message("python.sdk.configurator.frontend.choose.modules.text"),
                 projectStructureLabel = message("python.sdk.configurator.frontend.choose.modules.project.structure"),
                 environmentLabel = message("python.sdk.configurator.frontend.choose.modules.environment"))
    })
  }
}