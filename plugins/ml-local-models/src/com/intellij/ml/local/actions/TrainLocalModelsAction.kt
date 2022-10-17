package com.intellij.ml.local.actions

import com.intellij.lang.Language
import com.intellij.ml.local.MlLocalModelsBundle
import com.intellij.ml.local.models.LocalModelsTraining
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TrainLocalModelsAction : AnAction(MlLocalModelsBundle.message("ml.local.models.training.action")) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = !LocalModelsTraining.isTraining()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val language = Language.findLanguageByID("JAVA") ?: return
    //TODO: Dialog for different languages
    LocalModelsTraining.train(project, language)
  }
}