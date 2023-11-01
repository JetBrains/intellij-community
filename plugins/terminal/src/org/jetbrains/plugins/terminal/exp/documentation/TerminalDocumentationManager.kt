// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.documentation

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.idea.ActionsBundle
import com.intellij.lang.documentation.ide.actions.AdjustFontSizeAction
import com.intellij.lang.documentation.ide.documentationComponent
import com.intellij.lang.documentation.ide.impl.AdjusterPopupBoundsHandler
import com.intellij.lang.documentation.ide.impl.elementFlow
import com.intellij.lang.documentation.ide.ui.DocumentationComponent
import com.intellij.lang.documentation.ide.ui.scrollPaneWithCorner
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import org.jetbrains.terminal.completion.BaseSuggestion
import java.awt.Component
import java.awt.Dimension
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JScrollPane

/**
 * Logic is partially copied from [com.intellij.lang.documentation.ide.impl.DocumentationManager],
 * but simplified and adapted for Terminal needs.
 */
@Service(Service.Level.PROJECT)
internal class TerminalDocumentationManager(private val project: Project, private val scope: CoroutineScope) : Disposable {
  private var currentPopup: WeakReference<AbstractPopup>? = null

  // a separate scope is needed for the ability to cancel its children
  private val popupScope: CoroutineScope = scope.childScope()

  private fun getCurrentPopup(): AbstractPopup? {
    EDT.assertIsEdt()
    val popup: AbstractPopup? = currentPopup?.get()
    if (popup == null) {
      return null
    }
    if (!popup.isVisible) {
      // hint's window might've been hidden by AWT without notifying us
      // dispose to remove the popup from IDE hierarchy and avoid leaking components
      popup.cancel()
      check(this.currentPopup == null)  // see popup child disposable in showDocumentationPopup method
      return null
    }
    return popup
  }

  private fun setCurrentPopup(popup: AbstractPopup) {
    EDT.assertIsEdt()
    currentPopup = WeakReference(popup)
  }

  @RequiresEdt
  fun autoShowDocumentationOnItemChange(lookup: LookupEx, parentDisposable: Disposable) {
    if (!TerminalUiSettingsManager.getInstance().autoShowDocumentationPopup) {
      return
    }
    val lookupElementFlow = lookup.elementFlow()
    val showDocJob = scope.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
      lookupElementFlow.collectLatest {
        showDocumentationForItem(lookup, it, allowEmpty = false, parentDisposable)
      }
    }
    Disposer.register(parentDisposable) {
      showDocJob.cancel()
    }
  }

  /** @param [allowEmpty] whether to show "No documentation" popup when documentation is not found for initial [element] */
  @RequiresEdt
  fun showDocumentationForItem(lookup: LookupEx, element: LookupElement, allowEmpty: Boolean, parentDisposable: Disposable) {
    if (getCurrentPopup() != null) {
      return
    }
    val docRequest = element.toDocRequest()
                     ?: if (allowEmpty) EmptyDocTarget.request else return
    popupScope.coroutineContext.job.cancelChildren()
    val popup = showDocumentationPopup(docRequest, lookup, parentDisposable)
    setCurrentPopup(popup)
  }

  private fun showDocumentationPopup(request: DocumentationRequest, lookup: LookupEx, parentDisposable: Disposable): AbstractPopup {
    val docComponent = documentationComponent(project, request.targetPointer, request.presentation, parentDisposable)
    val popupComponent = createDocPopupComponent(docComponent, parentDisposable)
    val popup = createDocPopup(lookup.project, popupComponent)
    val boundsHandler = AdjusterPopupBoundsHandler(lookup.component)

    // drop the current lookup element, because documentation for it will be shown initially
    val docRequestFlow: Flow<DocumentationRequest?> = lookup.elementFlow().drop(1).map { it.toDocRequest() }
    popupScope.launch(Dispatchers.Default) {
      docRequestFlow.collectLatest {
        handleDocRequest(docComponent, it)
      }
    }
    popupScope.launch(Dispatchers.EDT) {
      docComponent.contentSizeUpdates.collectLatest {
        boundsHandler.updatePopup(popup, false)
      }
    }
    Disposer.register(parentDisposable) {
      cancelPopup()
    }
    Disposer.register(popup) {
      EDT.assertIsEdt()
      currentPopup = null
      popupScope.coroutineContext.job.cancelChildren()
    }

    boundsHandler.showPopup(popup)
    return popup
  }

  private fun handleDocRequest(docComponent: DocumentationComponent, request: DocumentationRequest?) {
    if (request != null) {
      docComponent.resetBrowser(request.targetPointer, request.presentation)
    }
    else popupScope.launch(Dispatchers.EDT) {
      cancelPopup() // cancel popup if there is no documentation for the selected item
    }
  }

  private fun cancelPopup() {
    EDT.assertIsEdt()
    getCurrentPopup()?.cancel()
  }

  private fun createDocPopupComponent(docComponent: DocumentationComponent, parentDisposable: Disposable): JComponent {
    val actions = DefaultActionGroup().apply {
      isPopup = true
      add(TerminalToggleAutoShowDocumentationAction())
      add(AdjustFontSizeAction())
    }
    val scrollPane = docComponent.getComponent() as? JScrollPane ?: error("JScrollPane expected")
    val moreButton = actionButton(actions, scrollPane.viewport.view)
    return scrollPaneWithCorner(parentDisposable, scrollPane, moreButton)
  }

  private fun createDocPopup(project: Project, component: JComponent): AbstractPopup {
    val builder = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(component, null)
      .setProject(project)
      .setResizable(true)
      .setMovable(true)
      .setModalContext(false)
      .setRequestFocus(false) // otherwise, it won't be possible to continue interacting with lookup/SE
      .setCancelOnClickOutside(false) // to not close the popup when selecting lookup elements by mouse
    return builder.createPopup() as AbstractPopup
  }

  private fun actionButton(actions: ActionGroup, contextComponent: Component): JComponent {
    val presentation = Presentation().also {
      it.icon = AllIcons.Actions.More
      it.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
    }
    val button = object : ActionButton(actions, presentation, ActionPlaces.UNKNOWN, Dimension(20, 20)) {
      override fun getDataContext(): DataContext = DataManager.getInstance().getDataContext(contextComponent)
    }
    button.setNoIconsInPopup(true)
    return button
  }

  private fun LookupElement.toDocRequest(): DocumentationRequest? {
    val suggestion = `object` as? BaseSuggestion ?: return null
    val description = suggestion.description ?: return null
    val docTarget = TerminalDocumentationTarget(suggestion.names.first(), description)
    return DocumentationRequest(docTarget.createPointer(), docTarget.computePresentation())
  }

  override fun dispose() {
    scope.cancel()
  }

  private object EmptyDocTarget : DocumentationTarget {
    val request: DocumentationRequest = DocumentationRequest(createPointer(), computePresentation())

    override fun createPointer(): Pointer<out DocumentationTarget> {
      return Pointer.hardPointer(this)
    }

    override fun computePresentation(): TargetPresentation {
      return TargetPresentation.builder("").presentation()
    }

    override fun computeDocumentation(): DocumentationResult? = null  // return null here to treat the request as "empty"
  }

  companion object {
    fun getInstance(project: Project): TerminalDocumentationManager = project.service()
  }
}

private class TerminalToggleAutoShowDocumentationAction : ToggleAction(
  ActionsBundle.actionText("Documentation.ToggleAutoShow"),
  ActionsBundle.actionDescription("Documentation.ToggleAutoShow"),
  null
), HintManagerImpl.ActionToIgnore {
  override fun isSelected(e: AnActionEvent): Boolean {
    return TerminalUiSettingsManager.getInstance().autoShowDocumentationPopup
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    TerminalUiSettingsManager.getInstance().autoShowDocumentationPopup = state
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val visible = project != null && LookupManager.getInstance(project).activeLookup != null
    e.presentation.isEnabledAndVisible = visible
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}