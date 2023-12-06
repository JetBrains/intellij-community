// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateEditorBase
import com.intellij.ide.util.gotoByName.*
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyGotoClassContributor
import com.jetbrains.python.codeInsight.postfix.PyPostfixTemplateExpressionCondition.PyClassCondition.Companion.create
import com.jetbrains.python.psi.PyClass
import javax.swing.JComponent

class PyPostfixTemplateEditor(provider: PyPostfixTemplateProvider) :
  PostfixTemplateEditorBase<PyPostfixTemplateExpressionCondition?>(provider, true) {

  override fun fillConditions(group: DefaultActionGroup) {
    for (condition in PyPostfixTemplateExpressionCondition.PUBLIC_CONDITIONS.values) {
      group.add(AddConditionAction(condition))
    }
    val projects = ProjectManager.getInstance().openProjects
    if (projects.isNotEmpty()) {
      group.add(ChooseClassAction(projects))
    }
    group.add(EnterClassAction())
  }

  override fun createTemplate(templateId: String, templateName: String): PyEditablePostfixTemplate {
    val templateText = myTemplateEditor.document.text
    val conditions = LinkedHashSet(myExpressionTypesListModel.elements().toList())
    val useTopmostExpression = myApplyToTheTopmostJBCheckBox.isSelected
    return PyEditablePostfixTemplate(templateId, templateName, templateText, "", conditions, useTopmostExpression, myProvider, false)
  }

  override fun getComponent(): JComponent {
    return myEditTemplateAndConditionsPanel
  }

  private inner class ChooseClassAction(private val projects: Array<Project>) : DumbAwareAction(
    PyBundle.messagePointer("settings.postfix.choose.class.action.name")) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val contributor = MultiProjectPyClassesContributor(projects)
      val model: GotoClassModel2 = object : GotoClassModel2(project) {
        override fun getPromptText(): String {
          return PyBundle.message("settings.postfix.choose.class.prompt.text")
        }

        override fun getContributorList(): List<ChooseByNameContributor> {
          return listOf<ChooseByNameContributor>(contributor)
        }

        override fun getCheckBoxName(): String? {
          return null // don't show checkbox, always search in libraries
        }
      }
      val popup = Companion.createPopup(project, model)
      popup.invoke(object : ChooseByNamePopupComponent.Callback() {
        override fun elementChosen(element: Any) {}
        override fun onClose() {
          if (!popup.closedCorrectly) {
            return
          }
          val chosenElement = popup.chosenElement
          if (chosenElement is PyClass) {
            val condition = create(chosenElement)
            if (condition != null) {
              myExpressionTypesListModel.addElement(condition)
            }
          }
        }
      }, ModalityState.current(), false)
    }
  }

  companion object {
    private fun createPopup(project: Project?, model: GotoClassModel2): ChooseClassByNamePopup {
      val provider: ChooseByNameItemProvider = DefaultChooseByNameItemProvider(null)
      val oldPopup = project?.getUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY)
      oldPopup?.close(false)
      val popup = ChooseClassByNamePopup(project, model, provider, oldPopup)
      project?.putUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, popup)
      popup.isSearchInAnyPlace = true
      return popup
    }
  }

  private class MultiProjectPyClassesContributor(private val projects: Array<Project>) : PyGotoClassContributor() {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
      for (project in projects) {
        super.processNames(processor, FindSymbolParameters.searchScopeFor(project, true), null)
      }
    }

    override fun processElementsWithName(name: String,
                                         processor: Processor<in NavigationItem?>,
                                         parameters: FindSymbolParameters) {
      for (project in projects) {
        val params = FindSymbolParameters(
          parameters.completePattern, parameters.localPatternName, FindSymbolParameters.searchScopeFor(project, true))
        super.processElementsWithName(name, processor, params)
      }
    }
  }

  private class ChooseClassByNamePopup(project: Project?,
                                       model: ChooseByNameModel,
                                       provider: ChooseByNameItemProvider,
                                       oldPopup: ChooseByNamePopup?) : ChooseByNamePopup(project, model, provider, oldPopup, null, false,
                                                                                         0) {
    var closedCorrectly = false
    override fun close(isOk: Boolean) {
      if (!checkDisposed()) {
        closedCorrectly = isOk
      }
      super.close(isOk)
    }
  }

  private inner class EnterClassAction : DumbAwareAction(
    PyBundle.messagePointer("settings.postfix.enter.class.action.name")) {
    override fun actionPerformed(e: AnActionEvent) {
      val name = Messages.showInputDialog(myEditTemplateAndConditionsPanel,
                                          PyBundle.message("settings.postfix.enter.fully.qualified.class.name"),
                                          PyBundle.message("settings.postfix.enter.class.dialog.name"), null)
      if (name != null) {
        val condition = create(name)
        if (condition != null) {
          myExpressionTypesListModel.addElement(condition)
        }
      }
    }
  }
}
