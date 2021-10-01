// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ml.local

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.local.models.LocalModelsManager
import com.intellij.ml.local.models.frequency.classes.ClassesFrequencyLocalModel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

class JavaFrequencyElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "local"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement
    val receiverClassName = contextFeatures.getUserData(JavaFrequencyContextFeatureProvider.RECEIVER_CLASS_NAME_KEY)
    val classFrequencies = contextFeatures.getUserData(JavaFrequencyContextFeatureProvider.RECEIVER_CLASS_FREQUENCIES_KEY)
    if (psi is PsiMethod && receiverClassName != null && classFrequencies != null) {
      psi.containingClass?.let { cls ->
        JavaLocalModelsUtil.getClassName(cls)?.let { className ->
          if (receiverClassName == className) {
            JavaLocalModelsUtil.getMethodName(psi)?.let { methodName ->
              val frequency = classFrequencies.getMethodFrequency(methodName)
              if (frequency > 0) {
                val totalUsages = classFrequencies.getTotalFrequency()
                features["absolute_method_frequency"] = MLFeatureValue.numerical(frequency)
                features["relative_method_frequency"] = MLFeatureValue.numerical(frequency.toDouble() / totalUsages)
              }
            }
          }
        }
      }
    }
    val classesModel = LocalModelsManager.getInstance(location.project).getModel<ClassesFrequencyLocalModel>(JavaLanguage.INSTANCE)
    if (psi is PsiClass && classesModel != null && classesModel.readyToUse()) {
      JavaLocalModelsUtil.getClassName(psi)?.let { className ->
        classesModel.getClass(className)?.let {
          features["absolute_class_frequency"] = MLFeatureValue.numerical(it)
          features["relative_class_frequency"] = MLFeatureValue.numerical(it.toDouble() / classesModel.totalClassesUsages())
        }
      }
    }
    return features
  }
}