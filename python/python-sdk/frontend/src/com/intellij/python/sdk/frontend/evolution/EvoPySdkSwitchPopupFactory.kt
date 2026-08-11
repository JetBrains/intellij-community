package com.intellij.python.sdk.frontend.evolution

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.getAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSdkDto
import com.intellij.python.sdk.common.evolution.requestEvoNode
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoErrorException
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreePopup
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeSection
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoWarningException
import com.intellij.python.sdk.frontend.icons.PythonSdkFrontendIcons
import kotlinx.coroutines.CoroutineScope
import kotlin.collections.plus

private val managePackagesAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.python.packaging.interpreter.widget.manage.packages") },
  { "" },
  PythonSdkFrontendIcons.PythonPackages,
) {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let {
      ToolWindowManager.getInstance(it).getToolWindow("Python Packages")?.show()
    }
  }
}

private val packageManagerActions: List<AnAction> = listOf("PoetryLockAction", "PoetryUpdateAction").mapNotNull { getAction(it) }

private fun EvoLeafDto.toStubAction(): AnAction = object : AnAction({ title }, { description ?: "" }, icon.icon()) {
  init {
    secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) {}
}

private fun EvoLeafDto.toElement(): EvoTreeElement = when (kind) {
  EvoLeafKind.SELECT_ENV -> EvoTreeLeafElement(SelectEnvAction(requireNotNull(sdk) { "SELECT_ENV leaf without sdk" }))
  EvoLeafKind.ACTION -> EvoTreeLeafElement(toStubAction())
}

private fun EvoLoadResultDto.toSections(): List<EvoTreeSection> = when (this) {
  is EvoLoadResultDto.Ok -> sections.map { section ->
    val leaves = section.leaves.map { it.toElement() }
    val withAddNew = if (section.addNew) leaves + EvoTreeLeafElement(AddNewEnvAction()) else leaves
    EvoTreeSection(label = section.label?.let { ListSeparator(it) }, elements = withAddNew)
  }
  is EvoLoadResultDto.Warning -> throw EvoWarningException(message)
  is EvoLoadResultDto.Error -> throw EvoErrorException(message)
}

internal class EvoPySdkSwitchPopupFactory(
  val project: Project,
  val moduleName: String,
  val currentSdk: EvoSdkDto?,
  val nodes: List<EvoNodeDto>,
  val scope: CoroutineScope,
) {

  private fun buildTree(): EvoTreeStaticNodeElement {
    val projectId = project.projectId()

    val externalToolsSection = EvoTreeSection(
      label = ListSeparator(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.select.environment")),
      elements = nodes.map { node ->
        EvoTreeLazyNodeElement(node.label, node.icon.icon()) {
          requestEvoNode(projectId, moduleName, node.id).toSections()
        }
      },
    )

    val currentEnvSection = when (currentSdk) {
      null -> EvoTreeSection(
        label = ListSeparator(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts")),
        elements = listOf(
          EvoTreeLeafElement(defaultUvAction),
          EvoTreeLeafElement(autoSetupWithAIAction),
        )
      )
      else -> EvoTreeSection(
        label = ListSeparator(currentSdk.getCurrentTitle(), currentSdk.icon.icon()),
        elements = packageManagerActions.map { EvoTreeLeafElement(it) } + EvoTreeLeafElement(managePackagesAction),
      )
    }

    return EvoTreeStaticNodeElement(
      text = "",
      icon = PythonSdkFrontendIcons.Logo,
      sections = listOf(externalToolsSection, currentEnvSection),
    )
  }

  fun createPopup(context: DataContext): ListPopup {
    val tree = buildTree()
    return EvoSdkManagerTreePopup(
      title = moduleName,
      evoTreeNodeElement = tree,
      dataContext = context,
      scope = scope,
    ).apply {
      setExecuteExpandedItemOnClick(true)
    }
  }
}

internal class EvoSdkManagerTreePopup(
  title: @PopupTitle String?,
  evoTreeNodeElement: EvoTreeNodeElement,
  dataContext: DataContext,
  disposeCallback: (() -> Unit)? = null,
  scope: CoroutineScope,
) : EvoTreePopup(
  parentPopup = null,
  title = title,
  evoTreeNodeElement = evoTreeNodeElement,
  dataContext = dataContext,
  scope = scope,
  maxRowCount = -1,
  disposeCallback = disposeCallback,
)
