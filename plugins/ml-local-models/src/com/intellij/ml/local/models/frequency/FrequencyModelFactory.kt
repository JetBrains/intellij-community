package com.intellij.ml.local.models.frequency

import com.intellij.ml.local.models.api.LocalModelBuilder
import com.intellij.lang.Language
import com.intellij.ml.local.models.api.LocalModelFactory
import com.intellij.ml.local.models.frequency.classes.ClassesFrequencyModelFactory
import com.intellij.ml.local.models.frequency.methods.MethodsFrequencyModelFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor

abstract class FrequencyModelFactory<UsagesTracker> : LocalModelFactory {

  protected abstract fun fileVisitor(usagesTracker: UsagesTracker): PsiElementVisitor

  abstract override fun modelBuilder(project: Project, language: Language): LocalModelBuilder
}