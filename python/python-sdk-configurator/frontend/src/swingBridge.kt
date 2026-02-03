package com.intellij.python.sdkConfigurator.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.WindowManager
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.frontend.PySdkConfiguratorFrontendBundle.message
import com.intellij.python.sdkConfigurator.frontend.components.ModuleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import java.awt.Dimension
import javax.swing.JComponent

internal suspend fun askUser(project: Project, modules: ModulesDTO, onResult: (Set<ModuleName>) -> Unit) {
  val viewModel = withContext(Dispatchers.Default) {
    ModulesViewModel(modules)
  }
  withContext(Dispatchers.EDT) {
    val myDialog = MyDialog(project, viewModel)
    if (myDialog.showAndGet()) {
      onResult(viewModel.checkedModules)
    }
  }
}

private class MyDialog(private val project: Project, private val viewModel: ModulesViewModel) : DialogWrapper(project) {

  init {
    title = message("python.sdk.configurator.frontend.choose.modules.title")
    setOKButtonText(message("python.sdk.configurator.frontend.choose.modules.configure"))
    init()
  }

  override fun dispose() {
    super.dispose()
    viewModel.okButtonEnabledListener = null
  }

  @OptIn(ExperimentalJewelApi::class)
  override fun createCenterPanel(): JComponent {
    enableNewSwingCompositing()
    viewModel.okButtonEnabledListener = { enabled ->
      isOKActionEnabled = enabled // To enable/disable "OK" button
    }
    val screen = WindowManager.getInstance().getFrame(project)!! // Have no idea why could it be null
    return compose(focusOnClickInside = true, config = {
      // 65% according to Lena
      preferredSize = Dimension(screen.width / 2, (screen.height * 0.65f).toInt())
    }) {
      ModuleList(viewModel)
    }
  }
}