package com.intellij.ml.local.actions

import com.intellij.ml.local.MlLocalModelsBundle
import com.intellij.ml.local.models.LocalModelsTraining
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TrainLocalModelsAction : AnAction(MlLocalModelsBundle.message("ml.local.models.training.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (LocalModelsTraining.isTraining()) {
      //TODO: Show message that model is training right now
      return
    }
    //TODO: Dialog for different languages
    LocalModelsTraining.train(project, Language.findLanguageByID("JAVA")!!)
    //TODO: Show message that model trained successfully
  }
}