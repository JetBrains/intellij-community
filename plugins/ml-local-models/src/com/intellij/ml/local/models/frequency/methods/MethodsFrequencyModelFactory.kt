package com.intellij.ml.local.models.frequency.methods

import com.intellij.ml.local.models.api.LocalModel
import com.intellij.ml.local.models.api.LocalModelBuilder
import com.intellij.ml.local.models.frequency.FrequencyModelFactory
import com.intellij.ml.local.util.StorageUtil
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor

abstract class MethodsFrequencyModelFactory : FrequencyModelFactory<MethodsUsagesTracker>() {

  override val id: String = MethodsFrequencyLocalModel.ID

  abstract override fun fileVisitor(usagesTracker: MethodsUsagesTracker): PsiElementVisitor

  override fun modelBuilder(project: Project, language: Language): LocalModelBuilder {
    val storagesPath = StorageUtil.storagePath(project, language)
    val storage = MethodsFrequencyStorage.getStorage(storagesPath)

    return object : LocalModelBuilder {

      override fun onStarted() {
        storage.setValid(false)
      }

      override fun onFinished() {
        storage.setValid(true)
      }

      override fun fileVisitor(): PsiElementVisitor = fileVisitor(MethodsUsagesTracker(storage))

      override fun build(): LocalModel? {
        if (!storage.isValid() || storage.isEmpty()) {
          return null
        }
        return MethodsFrequencyLocalModel(storage)
      }
    }
  }
}