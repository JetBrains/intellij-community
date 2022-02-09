package com.intellij.refactoring.detector.semantic.diff

import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.tools.combined.CombinedDiffBlock
import com.intellij.diff.tools.combined.CombinedDiffBlockContent
import com.intellij.diff.tools.combined.CombinedDiffBlockFactory
import com.intellij.diff.tools.combined.editors
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CloseTabToolbarAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JPanel

internal class SemanticDiffFragmentBlockFactory : CombinedDiffBlockFactory {
  override fun isApplicable(content: CombinedDiffBlockContent): Boolean {
    val viewer = (content.viewer as? DiffViewerBase) ?: return false

    return viewer.request is SemanticFragmentDiffRequest
  }

  override fun createBlock(content: CombinedDiffBlockContent, withBorder: Boolean): CombinedDiffBlock {
    val request = (content.viewer as DiffViewerBase).request as SemanticFragmentDiffRequest

    return SemanticCombinedDiffBlock(request.title, content, request.closeAction)
  }

}

internal class SemanticCombinedDiffBlock(val title: String, override val content: CombinedDiffBlockContent, onCloseAction: () -> Unit) :
  JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true)), CombinedDiffBlock {

  override val header = SemanticCombinedDiffHeader(title, content.viewer) { onCloseAction(); Disposer.dispose(this) }
  override val body = content.viewer.component

  init {
    add(header)
    add(body)
  }

  override val component = this
  override fun dispose() {}
}

internal class SemanticCombinedDiffHeader(title: @NlsSafe String, viewer: DiffViewer, closeAction: () -> Unit) : BorderLayoutPanel() {
  init {
    background = UIUtil.getListBackground()
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
    addToCenter(SimpleColoredComponent().append(title))
    val rightToolbarGroup = DefaultActionGroup()
    val myCloseAction = MyCloseAction(closeAction)
    viewer.editors.forEach { myCloseAction.registerCustomShortcutSet(it.component, null) }
    rightToolbarGroup.add(myCloseAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("CombinedDiffHeaderRightToolbar", rightToolbarGroup, true)
    toolbar.targetComponent = this
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.component.isOpaque = false
    addToRight(toolbar.component)
  }

  private class MyCloseAction(private val closeAction: () -> Unit) : CloseTabToolbarAction(), RightAlignedToolbarAction {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.icon = AllIcons.Actions.CloseDarkGrey
      e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      closeAction()
    }
  }
}
