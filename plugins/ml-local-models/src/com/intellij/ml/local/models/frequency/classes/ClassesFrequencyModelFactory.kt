package com.intellij.ml.local.models.frequency.classes

import com.intellij.ml.local.models.api.LocalModelBuilder
import com.intellij.ml.local.models.frequency.FrequencyModelFactory
import com.intellij.ml.local.util.StorageUtil
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor

abstract class ClassesFrequencyModelFactory : FrequencyModelFactory<ClassesUsagesTracker>() {

  override val id: String = ClassesFrequencyLocalModel.ID

  abstract override fun fileVisitor(usagesTracker: ClassesUsagesTracker): PsiElementVisitor

  override fun modelBuilder(project: Project, language: Language): LocalModelBuilder {
    val storagesPath = StorageUtil.storagePath(project, language)
    val storage = ClassesFrequencyStorage.getStorage(storagesPath)

    return object : LocalModelBuilder {

      override fun onStarted() {
        storage.setValid(false)
      }

      override fun onFinished() {
        storage.setValid(true)
      }

      override fun fileVisitor(): PsiElementVisitor = fileVisitor(ClassesUsagesTracker(storage))

      override fun build(): ClassesFrequencyLocalModel? {
        if (!storage.isValid() || storage.isEmpty()) {
          return null
        }
        return ClassesFrequencyLocalModel(storage)
      }
    }
  }
}