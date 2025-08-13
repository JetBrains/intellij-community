package com.intellij.python.sdk.ui.evolution.ui

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.getAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.sdk.ui.PySdkUiBundle
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreePopup
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeStaticNodeElement
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.documentation.PyDocumentationSettings
import kotlinx.coroutines.CoroutineScope
import java.time.Duration

private val cache = Caffeine.newBuilder()
  .maximumSize(10)
  .expireAfterWrite(Duration.ofSeconds(3))
  .build { evoModuleSdk: EvoModuleSdk ->
    createTree(evoModuleSdk)
  }

fun getTree(evoModuleSdk: EvoModuleSdk): EvoTreeStaticNodeElement {
  val tree = cache.get(evoModuleSdk)
  return tree
}

private val managePackagesAction = object : AnAction(
  { PySdkUiBundle.message("evo.sdk.python.packaging.interpreter.widget.manage.packages") },
  { "" },
  PythonSdkUIIcons.PythonPackages,
) {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let {
      ToolWindowManager.getInstance(it).getToolWindow("Python Packages")?.show()
    }
  }
}

private val packageManagerActions: List<AnAction> = listOf("PoetryLockAction", "PoetryUpdateAction").mapNotNull { getAction(it) }

fun createTree(evoModuleSdk: EvoModuleSdk): EvoTreeStaticNodeElement {
  val currentEnvSection = when {
    evoModuleSdk.evoSdk != null -> {
      val actions = packageManagerActions.map { EvoTreeLeafElement(it) }
      EvoTreeSection(
        label = ListSeparator(evoModuleSdk.evoSdk.getCurrentTitle(), evoModuleSdk.evoSdk.icon),
        elements = actions + listOf(
          EvoTreeLeafElement(managePackagesAction),
        )
      )
    }
    else -> EvoTreeSection(
      ListSeparator(PySdkUiBundle.message("evo.sdk.status.bar.popup.shortcuts")),
      elements = listOf(
        //AutoconfigTreeElementProvider().getTreeElement(evoModuleSdk),
        EvoTreeLeafElement(defaultUvAction),
        EvoTreeLeafElement(autoSetupWithAIAction),
      )
    )
  }

  val externalToolsElements = EvoSelectSdkProvider.findAllRegistered().map { it.getTreeElement(evoModuleSdk) }
  val externalToolsSection = EvoTreeSection(
    label = ListSeparator(PySdkUiBundle.message("evo.sdk.status.bar.popup.select.environment")),
    elements = externalToolsElements
  )


  val tree = EvoTreeStaticNodeElement(
    text = "",
    icon = PythonSdkUIIcons.Logo,
    sections = listOf(
      externalToolsSection,
      currentEnvSection,
    )
  )
  return tree
}

internal class EvoPySdkSwitchPopupFactory(
  val pySdk: Sdk?,
  val evoModuleSdk: EvoModuleSdk,
  val isMultiModules: Boolean,
  val scope: CoroutineScope,
) {


  @Suppress("UnstableApiUsage")
  fun createPopup(context: DataContext): ListPopup {
    //val group = DefaultActionGroup()

    //val legacyActions = DefaultActionGroup(
    //  "Legacy Actions",
    //  "Legacy actions",
    //  EvolutionIcons.Logo
    //).apply {
    //  isPopup = true
    //  //val baseIdeActions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(evoModuleSdk.module)) { sdk ->
    //  //  SlowOperations.knownIssue("PY-76167").use {
    //  //    switchToSdk(evoModuleSdk.module, sdk, pySdk)
    //  //  }
    //  //}
    //  //addAll(baseIdeActions)
    //}
    //group.add(legacyActions)


    val tree = getTree(evoModuleSdk)
    val listPopup = EvoSdkManagerTreePopup(
      title = "${evoModuleSdk.module.name}", // ᴰᴷ  ˢᵈᵏ ₛₖ ˢᵈᵏ 	ˢᵈᵏ
      evoTreeNodeElement = tree,
      dataContext = context,
      scope = scope,
      disposeCallback = { cache.put(evoModuleSdk, tree) },
    ).apply {
      //setCaptionIcon(PythonEvolutionIcons.Evo)
      //setCaptionIconPosition(false)
      setExecuteExpandedItemOnClick(true)
      //setAdText("${evoModuleSdk.module.name} sdk", SwingConstants.TRAILING)
      //setIsMovable(false)
    }
    PyDocumentationSettings.getInstance(evoModuleSdk.module)
    return listPopup
  }
}

interface EvoSelectSdkProvider {
  fun getTreeElement(evoModuleSdk: EvoModuleSdk): EvoTreeElement

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName.create<EvoSelectSdkProvider>("Pythonid.evoTreeElementProvider")

    @JvmStatic
    fun findAllRegistered(): List<EvoSelectSdkProvider>  {
      return EP_NAME.extensionsIfPointIsRegistered
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


