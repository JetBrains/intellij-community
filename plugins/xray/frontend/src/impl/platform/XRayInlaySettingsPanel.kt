// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.codeInsight.hints.getLanguageForSettingKey
import com.intellij.codeInsight.hints.settings.InlayGroupSettingProvider
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.codeInsight.hints.settings.language.createEditor
import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorTreeRendererContextProvider
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBSlider
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xray.frontend.api.InlayHintStage
import com.intellij.xray.frontend.api.XRayBundle
import com.intellij.xray.frontend.impl.action.stageName
import com.intellij.xray.frontend.impl.service.XRayModeService
import com.intellij.xray.frontend.impl.service.XRayStageStore
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Hashtable
import java.util.concurrent.Callable
import java.util.function.Predicate
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

val CASE_KEY: Key<ImmediateConfigurable.Case> = Key.create("inlay.case.key")

internal class XRayInlaySettingsPanel(val project: Project) : JPanel(BorderLayout()) {

  val tree: CheckboxTree
  private val rightPanel: JPanel = JPanel(MigLayout("wrap, insets 0 10 0 0, gapy 20, fillx"))
  private val groups: MutableMap<InlayGroup, List<InlayProviderSettingsModel>>
  private var currentEditor: Editor? = null
  private val pendingStages = HashMap<InlayHintSettingsKey, InlayHintStage>()
  private val pendingGroupStages = HashMap<String, InlayHintStage>()

  @ApiStatus.Internal
  companion object {
    @JvmField
    val PREVIEW_KEY: Key<Any> = Key.create("inlay.preview.key")

    fun getFileTypeForPreview(model: InlayProviderSettingsModel): LanguageFileType {
      return model.getCasePreviewLanguage(null)?.associatedFileType ?: PlainTextFileType.INSTANCE
    }
  }

  init {
    val models = InlaySettingsProvider.EP.getExtensions().flatMap { provider ->
      provider.getSupportedLanguages(project).flatMap { provider.createModels(project, it) }
    }
    groups = models.groupBy { it.group }.toSortedMap()
    val globalSettings = groups.keys.associateWith { InlayGroupSettingProvider.EP.findForGroup(it) }

    val root = XRayTreeNode()
    val lastSelected = InlayHintsSettings.instance().getLastViewedProviderId()
    var nodeToSelect: CheckedTreeNode? = null

    // filling code vision settings
    if (Registry.`is`("editor.codeVision.new")) {
      groups.remove(InlayGroup.CODE_VISION_GROUP)
    }

    for (group in groups) {
      val groupNode = XRayTreeNode(globalSettings[group.key] ?: group.key, StageKey.GroupSettingsKey(group.key.key))
      root.add(groupNode)
      val primaryLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
      val sortedMap = group.value.groupBy { it.language }.toSortedMap(
        Comparator { o1, o2 ->
          val primary = compareValues(primaryLanguages.contains(o2), primaryLanguages.contains(o1))
          if (primary != 0) primary
          else compareValues(o1.displayName, o2.displayName)
        })
      for (lang in sortedMap) {
        val firstModel = lang.value.first()
        val langNode: XRayTreeNode
        val startFrom: Int
        if ((lang.value.size == 1 || group.key.toString() == firstModel.name && firstModel.language == sortedMap.firstKey()) &&
            InlayGroup.OTHER_GROUP != group.key) {
          nodeToSelect = addModelNode(firstModel, groupNode, lastSelected, nodeToSelect)
          firstModel.isMergedNode = true
          langNode = groupNode.firstChild as XRayTreeNode
          startFrom = 1
        }
        else if (lang.key == Language.ANY) {
          langNode = groupNode
          startFrom = 0
        }
        else {
          langNode = XRayTreeNode(lang.key, StageKey.GroupSettingsKey(getLanguageForSettingKey(lang.key).id))
          groupNode.add(langNode)
          startFrom = 0
        }

        for (it in startFrom until lang.value.size) {
          nodeToSelect = addModelNode(lang.value[it], langNode, lastSelected, nodeToSelect)
        }
      }
    }

    tree = object : CheckboxTree(InlaySettingsTreeRenderer(), root, CheckPolicy(false, false, false, false)) {
      override fun installSpeedSearch() {
        TreeSpeedSearch.installOn(this, true) {
          getName(it.lastPathComponent as DefaultMutableTreeNode,
                  it.parentPath?.lastPathComponent as DefaultMutableTreeNode?)
        }
      }

      override fun onNodeStateChanged(node: CheckedTreeNode?) {
        super.onNodeStateChanged(node)
        (node as? XRayTreeNode)?.let { xRayNode ->
          xRayNode.stageKey?.let {
            propagateStage(xRayNode, xRayNode.stage)
            if (tree.lastSelectedPathComponent === xRayNode) {
              updateRightPanel(xRayNode)
            }
          }
        }
      }
    }
    tree.addTreeSelectionListener(
      TreeSelectionListener { updateRightPanel(it?.newLeadSelectionPath?.lastPathComponent as? CheckedTreeNode) })
    if (nodeToSelect == null) {
      TreeUtil.expand(tree, 1)
    }
    else {
      TreeUtil.selectNode(tree, nodeToSelect)
    }

    val splitter = JBSplitter(false, "inlay.settings.proportion.key", 0.45f)
    splitter.setHonorComponentsMinimumSize(false)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                 ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    splitter.secondComponent = rightPanel
    add(splitter, BorderLayout.CENTER)
  }

  @Nls
  private fun getName(node: DefaultMutableTreeNode?, parent: DefaultMutableTreeNode?): String {
    when (val item = node?.userObject) {
      is InlayGroupSettingProvider -> return item.group.title()
      is InlayGroup -> return item.title()
      is Language -> return item.displayName
      is InlayProviderSettingsModel -> return if (parent?.userObject is InlayGroup) item.language.displayName else item.name
      is ImmediateConfigurable.Case -> return item.name
    }
    return ""
  }

  private fun addModelNode(
    model: InlayProviderSettingsModel,
    parent: CheckedTreeNode,
    lastId: String?,
    selected: CheckedTreeNode?,
  ): CheckedTreeNode? {
    var nodeToSelect: CheckedTreeNode? = selected
    model.onChangeListener = object : ChangeListener {
      override fun settingsChanged() {
        currentEditor?.let {
          val case = (it.getUserData(PREVIEW_KEY) as? CaseCheckedNode)?.userObject as? ImmediateConfigurable.Case
          updateHints(it, model, case)
        }
      }
    }

    val key: StageKey? = model.settingsKey(null)?.let { StageKey.LeafSettingsKey(it) }

    val node = object : XRayTreeNode(model, key) {
      override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        model.isEnabled = stage.isEnabled
        model.onChangeListener?.settingsChanged()
      }
    }

    parent.add(node)
    model.cases.forEach {
      val caseNode = CaseCheckedNode(it, { currentEditor }, model)
      node.add(caseNode)
      if (nodeToSelect == null && getProviderId(caseNode) == lastId) {
        nodeToSelect = caseNode
      }
    }
    return if (nodeToSelect == null && getProviderId(node) == lastId) node else nodeToSelect
  }

  private class CaseCheckedNode(
    private val case: ImmediateConfigurable.Case,
    private val editorProvider: () -> Editor?,
    private val model: InlayProviderSettingsModel,
  ) : XRayTreeNode(case, model.settingsKey(case)?.let { StageKey.LeafSettingsKey(it) }) {
    override fun setChecked(checked: Boolean) {
      super.setChecked(checked)
      case.value = stage.isEnabled
      if (PREVIEW_KEY.get(editorProvider()) == this) {
        model.onChangeListener?.settingsChanged()
      }
    }
  }

  private fun propagateStage(treeNode: CheckedTreeNode, stage: InlayHintStage) {

    (treeNode as? XRayTreeNode)?.let {
      if (it.stageKey != null) {
        it.stage = stage
      }
      else {
        it.isChecked = stage.isEnabled
      }
    }

    when (val item = treeNode.userObject) {
      is InlayGroup -> pendingGroupStages[item.key] = stage
      is InlayGroupSettingProvider -> pendingGroupStages[item.group.key] = stage
      is Language -> pendingGroupStages[getLanguageForSettingKey(item).id] = stage
      is InlayProviderSettingsModel -> {
        val key = item.settingsKey(null)
        if (key != null) {
          pendingStages[key] = stage
        }
        (tree.model as DefaultTreeModel).nodeChanged(treeNode)
      }
      is ImmediateConfigurable.Case -> {
        val parent = treeNode.parent as CheckedTreeNode
        val model = parent.userObject as InlayProviderSettingsModel
        val key = model.settingsKey(item)
        if (key != null) {
          pendingStages[key] = stage
        }
        (tree.model as DefaultTreeModel).nodeChanged(treeNode)
      }
    }
    treeNode.children().iterator().forEach { propagateStage(it as CheckedTreeNode, stage) }
  }

  private fun checkConsistency(treeNode: CheckedTreeNode, stage: InlayHintStage): Boolean {
    (treeNode as? XRayTreeNode)?.let {
      if (it.stageKey != null) {
        if (it.stage != stage) return false
      }
    }

    if (!treeNode.children().iterator().hasNext()) return true

    return treeNode.children().iterator().asSequence().map { checkConsistency(it as CheckedTreeNode, stage) }.fold(true, Boolean::and)
  }

  private fun updateRightPanel(treeNode: CheckedTreeNode?) {
    rightPanel.removeAll()
    currentEditor = null
    when (val item = treeNode?.userObject) {
      is InlayGroup -> {
        addDescription(item.description)
        val key = item.key
        val store = XRayStageStore.getInstanceOrNull()
        if (store != null) {
          addStageControl(treeNode,
                          { pendingGroupStages[key] ?: store.getGroupStage(key) },
                          { stage -> propagateStage(treeNode, stage) },
                          { })
        }
      }
      is InlayGroupSettingProvider -> {
        addDescription(item.group.description)

        val key = item.group.key
        val store = XRayStageStore.getInstanceOrNull()
        if (store != null) {
          addStageControl(treeNode,
                          { pendingGroupStages[key] ?: store.getGroupStage(key) },
                          { stage -> propagateStage(treeNode, stage) },
                          { })
        }

        rightPanel.add(item.component)
      }
      is Language -> {

        val key = getLanguageForSettingKey(item).id
        val store = XRayStageStore.getInstanceOrNull()
        if (store != null) {
          addStageControl(treeNode,
                          { pendingGroupStages[key] ?: store.getGroupStage(key) },
                          { stage -> propagateStage(treeNode, stage) },
                          { })
        }

        configureLanguageNode(treeNode)?.let {
          configurePreview((treeNode.firstChild as CheckedTreeNode).userObject as InlayProviderSettingsModel, treeNode)
        }
      }
      is InlayProviderSettingsModel -> {
        if (item.isMergedNode && item.description == null) {
          configureLanguageNode(treeNode)
        }
        if (item.description != null) {
          addDescription(item.description)
        }

        val key = item.settingsKey(null)
        val store = XRayStageStore.getInstanceOrNull()
        if (key != null && store != null) {
          val storeStage: (InlayHintStage) -> Unit =
            if (item.isMergedNode) { stage -> propagateStage(treeNode, stage) }
            else { stage -> pendingStages[key] = stage }
          val onChanged: () -> Unit = if (item.isMergedNode) ({ }) else ({ item.onChangeListener?.settingsChanged() })
          addStageControl(treeNode, { pendingStages[key] ?: store.getStage(key) }, storeStage, onChanged)
        }

        val component = item.component
        if (component !is JPanel || component.componentCount > 0) {
          component.border = JBUI.Borders.empty()
          rightPanel.add(component)
        }
        if (treeNode.isLeaf) {
          addPreview(item.getCasePreview(null) ?: item.previewText, item, null, treeNode)
        }
        else if (item.isMergedNode) {
          configurePreview(item, treeNode)
        }
      }
      is ImmediateConfigurable.Case -> {
        val parent = treeNode.parent as CheckedTreeNode
        val model = parent.userObject as InlayProviderSettingsModel
        addDescription(model.getCaseDescription(item))

        val key = model.settingsKey(item)
        val store = XRayStageStore.getInstanceOrNull()
        if (key != null && store != null) {
          addStageControl(treeNode,
                          { pendingStages[key] ?: store.getStage(key) },
                          { stage -> pendingStages[key] = stage },
                          { model.onChangeListener?.settingsChanged() })
        }

        val preview = model.getCasePreview(item)
        addPreview(preview, model, item, treeNode)
      }
    }
    if (treeNode != null) {
      InlayHintsSettings.instance().saveLastViewedProviderId(getProviderId(treeNode))
    }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun configurePreview(item: InlayProviderSettingsModel, treeNode: CheckedTreeNode) {
    val previewText = item.getCasePreview(null) ?: item.previewText
    if (previewText != null) {
      addPreview(previewText, item, null, treeNode)
    }
    else {
      for (case in item.cases) {
        val preview = item.getCasePreview(case)
        if (preview != null) {
          addPreview(preview, item, case, treeNode)
          break
        }
      }
    }
  }

  private fun configureLanguageNode(treeNode: CheckedTreeNode): String? {
    val description = ((treeNode.parent as CheckedTreeNode).userObject as InlayGroup).description
    addDescription(description)
    return description
  }

  private fun addPreview(
    previewText: String?,
    model: InlayProviderSettingsModel,
    case: ImmediateConfigurable.Case?,
    treeNode: CheckedTreeNode,
  ) {
    if (previewText != null) {
      val editorTextField = createEditor(model.getCasePreviewLanguage(null) ?: model.language, project) { editor ->
        currentEditor = editor
        PREVIEW_KEY.set(editor, treeNode)
        CASE_KEY.set(editor, case)
        updateHints(editor, model, case)
      }
      editorTextField.text = previewText
      editorTextField.addSettingsProvider {
        it.setBorder(JBUI.Borders.empty(10))
        it.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
        it.settings.apply {
          isLineNumbersShown = false
          isCaretRowShown = false
          isRightMarginShown = false
        }
      }
      rightPanel.add(ScrollPaneFactory.createScrollPane(editorTextField), "growx")
    }
  }

  private fun updateHints(editor: Editor, model: InlayProviderSettingsModel, case: ImmediateConfigurable.Case?) {
    val fileType = getFileTypeForPreview(model)
    ReadAction.nonBlocking(Callable {
      val file = model.createFile(project, fileType, editor.document, case?.id)
      val continuation = model.collectData(editor, file)
      continuation
    })
      .finishOnUiThread(ModalityState.stateForComponent(this)) { continuation ->
        ApplicationManager.getApplication().runWriteAction {
          continuation.run()
        }
      }
      .expireWhen { editor.isDisposed }
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun addDescription(@Nls s: String?) {
    val htmlLabel = SwingHelper.createHtmlLabel((s ?: ""), null, null)
    rightPanel.add(htmlLabel, "growy")
  }

  private fun stageVisibilitySettingsSummary(stage: InlayHintStage): @Nls String = when (stage) {
    InlayHintStage.ALWAYS -> XRayBundle.message("xray.inlay.stage.settings.summary.always")
    InlayHintStage.ESSENTIAL -> XRayBundle.message("xray.inlay.stage.settings.summary.essential")
    InlayHintStage.ADDITIONAL -> XRayBundle.message("xray.inlay.stage.settings.summary.additional")
    InlayHintStage.DISABLED -> XRayBundle.message("xray.inlay.stage.settings.summary.disabled")
  }

  private fun updateXRayCommentLabel(label: JBLabel, stage: InlayHintStage) {
    label.text = stageVisibilitySettingsSummary(stage)
  }

  /**
   * Adds the 4-way [InlayHintStage] control as a slider, ordered left-to-right from disabled to enabled by
   * increasing visibility (DISABLED -> ADDITIONAL -> ESSENTIAL -> ALWAYS). The tree checkbox is kept in sync
   * (`checked` <-> stage != [InlayHintStage.DISABLED]).
   */
  private fun addStageControl(
    treeNode: CheckedTreeNode,
    loadStage: () -> InlayHintStage,
    storeStage: (InlayHintStage) -> Unit,
    onChanged: () -> Unit,
  ) {
    // Slider positions, left (disabled / least visible) to right (always-on / most visible).
    val orderedStages = InlayHintStage.entries.reversed()
    val maxValue = orderedStages.size - 1

    val commentLabel = JBLabel()
    commentLabel.componentStyle = UIUtil.ComponentStyle.SMALL
    commentLabel.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    updateXRayCommentLabel(commentLabel, loadStage())

    val slider = JBSlider(0, maxValue)
    slider.snapToTicks = true
    slider.minorTickSpacing = 1
    slider.majorTickSpacing = 1
    slider.paintTicks = true
    slider.paintLabels = true

    val labelTable = Hashtable<Int, JBLabel>()
    for ((index, value) in orderedStages.withIndex()) {
      labelTable[index] = JBLabel(stageName(value))
    }

    slider.labelTable = labelTable as Hashtable<*, *>
    slider.updateUI()
    // Reserve vertical room for the tick labels by sizing the slider after the label table has been applied.
    slider.preferredSize = Dimension(JBUI.scale(300), slider.preferredSize.height)
    slider.value = orderedStages.indexOf(loadStage())

    slider.addChangeListener {
      if (slider.valueIsAdjusting) return@addChangeListener

      val selected = orderedStages[slider.value]
      if (selected == loadStage()) return@addChangeListener

      storeStage(selected)

      val xRayNode = treeNode as? XRayTreeNode
      if (xRayNode?.stageKey != null) {
        xRayNode.stage = selected
        (tree.model as DefaultTreeModel).nodeChanged(treeNode)
      }
      else if (treeNode.isChecked != selected.isEnabled) {
        treeNode.isChecked = selected.isEnabled
        (tree.model as DefaultTreeModel).nodeChanged(treeNode)
      }

      updateXRayCommentLabel(commentLabel, selected)
      onChanged()
      tree.repaint()
    }

    val col = JPanel(GridBagLayout())
    val c = GridBagConstraints()
    c.gridx = 0
    c.gridy = 0
    c.anchor = GridBagConstraints.WEST
    c.weightx = 1.0
    val visibilityLabel = JBLabel(XRayBundle.message("xray.inlay.stage.label"))
    col.add(visibilityLabel, c)

    c.gridy = 1
    c.insets = JBUI.insetsTop(6)
    col.add(slider, c)

    c.gridy = 2
    c.insets = JBUI.insets(10, 5, 0, 0)
    col.add(commentLabel, c)

    rightPanel.add(col, "growx")
  }

  private fun getProviderId(treeNode: CheckedTreeNode): String {
    when (val item = treeNode.userObject) {
      is InlayProviderSettingsModel -> {
        return item.language.id + "." + item.id
      }
      is ImmediateConfigurable.Case -> {
        val model = (treeNode.parent as CheckedTreeNode).userObject as InlayProviderSettingsModel
        return model.language.id + "." + model.id + "." + item.id
      }
    }
    return ""
  }

  private fun loadStages(store: XRayStageStore) {
    val root = tree.model.root as DefaultMutableTreeNode
    root.preorderEnumeration().iterator().forEach { node ->
      (node as? XRayTreeNode)?.let { xRayNode ->
        (xRayNode.stageKey)?.let { key ->
          xRayNode.stage = when (key) {
            is StageKey.LeafSettingsKey -> store.getStage(key.key)
            is StageKey.GroupSettingsKey -> store.getGroupStage(key.key)
          }
        }
      }
    }
    tree.repaint()
  }

  fun reset() {
    pendingStages.clear()
    pendingGroupStages.clear()
    XRayStageStore.getInstanceOrNull()?.let { loadStages(it) }
    reset(tree.model.root as XRayTreeNode, InlayHintsSettings.instance())
    updateRightPanel(tree.lastSelectedPathComponent as? CheckedTreeNode)
  }

  private fun reset(node: XRayTreeNode, settings: InlayHintsSettings) {
    val hasStageKey = node.stageKey != null
    when (val item = node.userObject) {
      is InlayGroupSettingProvider -> {
        item.reset()
        if (!hasStageKey) node.isChecked = item.isEnabled
      }
      is InlayProviderSettingsModel -> {
        item.reset()
        if (!hasStageKey) resetNode(node, isModelEnabled(item, settings))
      }
      is ImmediateConfigurable.Case -> {
        if (!hasStageKey) resetNode(node, isCaseEnabled(item, node.parent, settings))
      }
      is Language -> {
        if (!hasStageKey) resetNode(node, isLanguageEnabled(settings, item))
      }
    }
    node.children().toList().forEach {
      reset(it as XRayTreeNode, settings)
    }
    when (val item = node.userObject) {
      is InlayGroup -> {
        if (!hasStageKey) node.isChecked = groups[item]?.any { it.isEnabled } == true
      }
    }
  }

  private fun isCaseEnabled(item: ImmediateConfigurable.Case, parent: TreeNode, settings: InlayHintsSettings): Boolean {
    return item.value &&
           ((parent as CheckedTreeNode).userObject as InlayProviderSettingsModel).isEnabled &&
           settings.hintsEnabledGlobally()
  }

  private fun isModelEnabled(model: InlayProviderSettingsModel, settings: InlayHintsSettings): Boolean {
    return model.isEnabled && (!model.isMergedNode || settings.hintsEnabled(model.language)) && settings.hintsEnabledGlobally()
  }

  private fun resetNode(node: CheckedTreeNode, value: Boolean) {
    if (node.isChecked == value) return
    node.isChecked = value
    val treeModel = tree.model as DefaultTreeModel
    treeModel.nodeChanged(node)
    treeModel.nodeChanged(node.parent)
    if (node.parent != null) {
      treeModel.nodeChanged(node.parent.parent)
    }
  }

  fun apply() {
    val store = XRayStageStore.getInstanceOrNull()
    if (store != null) {
      for ((key, stage) in pendingStages) {
        store.setStage(key, stage)
      }
      for ((key, stage) in pendingGroupStages) {
        store.setGroupStage(key, stage)
      }
    }
    pendingStages.clear()
    pendingGroupStages.clear()

    apply(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    DeclarativeInlayHintsPassFactory.resetModificationStamp()
    InlayHintsPassFactoryInternal.restartDaemonUpdatingHints(project, "InlaySettingsPanel.apply()")

    val service = XRayModeService.getInstance()
    service.refreshProjectHints(project)
  }

  private fun apply(node: CheckedTreeNode, settings: InlayHintsSettings) {
    if (!hintsEnabledGlobally(node, settings)) {
      // skip settings applying if hints are disabled globally, and there is no enabled checkbox
      // it is needed to avoid overriding the settings by global inlay toggle
      return
    }
    node.children().toList().forEach { apply(it as CheckedTreeNode, settings) }
    when (val item = node.userObject) {
      is InlayGroupSettingProvider -> {
        item.isEnabled = node.isChecked
        item.apply()
      }
      is InlayProviderSettingsModel -> {
        item.isEnabled = node.isChecked
        item.apply()
        if (item.isMergedNode) {
          enableHintsForLanguage(item.language, settings, node)
        }
      }
      is ImmediateConfigurable.Case -> {
        // the combo already wrote the fine stage; the checkbox only forces DISABLED / promotes a stale DISABLED
        val parent = node.parent as CheckedTreeNode
        val model = parent.userObject as InlayProviderSettingsModel
        val key = model.settingsKey(item)
        key?.let {
          val store = XRayStageStore.getInstanceOrNull()
          store?.let {
            if (!node.isChecked) {
              store.setStage(key, InlayHintStage.DISABLED)
            }
            else if (store.getStage(key) == InlayHintStage.DISABLED) {
              store.setStage(key, InlayHintStage.ALWAYS)
            }
          }
          if (node.isChecked) {
            settings.setEnabledGlobally(true)
          }
        }
      }
      is Language -> {
        enableHintsForLanguage(item, settings, node)
      }
    }
  }

  private fun hintsEnabledGlobally(root: CheckedTreeNode, settings: InlayHintsSettings): Boolean {
    return settings.hintsEnabledGlobally() || isAnyCheckboxEnabled(root)
  }

  private fun isAnyCheckboxEnabled(node: CheckedTreeNode): Boolean {
    val children = node.children().toList()
    if (children.isEmpty()) {
      return when (node.userObject) {
        is InlayGroupSettingProvider,
        is InlayProviderSettingsModel,
        is ImmediateConfigurable.Case,
          -> {
          node.isChecked
        }
        else -> {
          false
        }
      }
    }
    else {
      return children.any { isAnyCheckboxEnabled(it as CheckedTreeNode) }
    }
  }

  private fun enableHintsForLanguage(language: Language, settings: InlayHintsSettings, node: CheckedTreeNode) {
    if (node.isChecked && !isLanguageEnabled(settings, language)) {
      settings.setHintsEnabledForLanguage(language, true)
    }
  }

  fun isModified(): Boolean {
    val store = XRayStageStore.getInstanceOrNull()
    if (store != null && (pendingStages.any { (key, stage) -> store.getStage(key) != stage } ||
                          pendingGroupStages.any { (key, stage) -> store.getGroupStage(key) != stage })) {
      return true
    }
    return isModified(tree.model.root as CheckedTreeNode, InlayHintsSettings.instance())
  }

  private fun isModified(node: CheckedTreeNode, settings: InlayHintsSettings): Boolean {
    when (val item = node.userObject) {
      is InlayGroupSettingProvider -> {
        if (item.isModified())
          return true
      }
      is InlayProviderSettingsModel -> {
        if (item.isModified() || (node.isChecked != isModelEnabled(item, settings)))
          return true
      }
      is ImmediateConfigurable.Case -> {
        if (node.isChecked != isCaseEnabled(item, node.parent, settings)) {
          return true
        }
      }
      is Language -> {
        if (isLanguageEnabled(settings, item) != node.isChecked)
          return true
      }
    }
    return node.children().toList().any { isModified(it as CheckedTreeNode, settings) }
  }

  private fun isLanguageEnabled(settings: InlayHintsSettings, item: Language) = settings.hintsEnabled(item)

  fun enableSearch(option: String?): Runnable? {
    if (option == null) return null
    return Runnable {
      val treeNode = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode, Condition {
        getName(it, it.parent as DefaultMutableTreeNode?).lowercase().startsWith(option.lowercase())
      })
      if (treeNode != null) {
        TreeUtil.selectNode(tree, treeNode)
      }
    }
  }

  fun selectModel(language: Language, selector: Predicate<InlayProviderSettingsModel>?) {
    val languages = LanguageUtil.getBaseLanguages(language).toSet()
    val node = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
      if (selector == null) {
        language in languages
      }
      else {
        val model = it.userObject as? InlayProviderSettingsModel
        model != null && selector.test(model) && model.language in languages
      }
    }
    if (node != null) {
      TreeUtil.selectNode(tree, node)
    }
  }

  private inner class InlaySettingsTreeRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true),
                                                  UiInspectorTreeRendererContextProvider {
    override fun customizeRenderer(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {

      val ui = (checkbox.ui as? XRayStageCheckBoxUI) ?: XRayStageCheckBoxUI().also { checkbox.ui = it }
      ui.stage = (value as? XRayTreeNode)?.takeIf { it.stageKey != null }?.stage
      ui.isInconsistent = ui.stage?.let { stage -> !checkConsistency(value as CheckedTreeNode, stage) } ?: false

      if (value !is DefaultMutableTreeNode) return

      val name = getName(value, value.parent as? DefaultMutableTreeNode)
      textRenderer.appendHTML(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getUiInspectorContext(tree: JTree, value: Any?, row: Int): List<PropertyBean> {
      if (value !is DefaultMutableTreeNode) return emptyList()
      val result = mutableListOf<PropertyBean>()

      when (val item = value.userObject) {
        is InlayGroupSettingProvider -> {
          result.add(PropertyBean("Inlay Group Key", item.group.key, true))
        }
        is InlayGroup -> {
          result.add(PropertyBean("Inlay Group Key", item.key, true))
        }
        is InlayProviderSettingsModel -> {
          result.add(PropertyBean("Inlay Provider Model ID", item.id, true))
        }
        is ImmediateConfigurable.Case -> {
          result.add(PropertyBean("Inlay ImmediateConfigurable ID", item.id, true))
        }
      }
      return result
    }
  }
}
