// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ml.local

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.local.models.LocalModelsManager
import com.intellij.ml.local.models.frequency.classes.ClassesFrequencyLocalModel
import com.intellij.ml.local.models.frequency.methods.MethodsFrequencyLocalModel
import com.intellij.ml.local.models.frequency.methods.MethodsFrequencies
import com.intellij.openapi.util.Key
import com.intellij.psi.*

class JavaFrequencyContextFeatureProvider : ContextFeatureProvider {
  companion object {
    val RECEIVER_CLASS_NAME_KEY: Key<String> = Key.create("ml.completion.local.models.receiver.class.name")
    val RECEIVER_CLASS_FREQUENCIES_KEY: Key<MethodsFrequencies> = Key.create("ml.completion.local.models.receiver.class.frequencies")
    private const val NAME = "frequency"
  }

  override fun getName(): String = NAME

  override fun calculateFeatures(environment: CompletionEnvironment): MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val project = environment.parameters.position.project
    val modelsManager = LocalModelsManager.getInstance(project)
    val methodsModel = modelsManager.getModel<MethodsFrequencyLocalModel>(JavaLanguage.INSTANCE)
    if (methodsModel != null && methodsModel.readyToUse()) {
      getReceiverClass(environment.parameters)?.let { cls ->
        JavaLocalModelsUtil.getClassName(cls)?.let {
          environment.putUserData(RECEIVER_CLASS_NAME_KEY, it)
          methodsModel.getMethodsByClass(it)?.let { frequencies ->
            environment.putUserData(RECEIVER_CLASS_FREQUENCIES_KEY, frequencies)
          }
        }
      }
      features["total_methods"] = MLFeatureValue.numerical(methodsModel.totalMethodsCount())
      features["total_methods_usages"] = MLFeatureValue.numerical(methodsModel.totalMethodsUsages())
    }
    val classesModel = modelsManager.getModel<ClassesFrequencyLocalModel>(JavaLanguage.INSTANCE)
    if (classesModel != null && classesModel.readyToUse()) {
      features["total_classes"] = MLFeatureValue.numerical(classesModel.totalClassesCount())
      features["total_classes_usages"] = MLFeatureValue.numerical(classesModel.totalClassesUsages())
    }
    return features
  }

  private fun getReceiverClass(parameters: CompletionParameters): PsiClass? {
    getQualifierExpression(parameters)?.let {
      if (it is PsiCallExpression) {
        return (it.resolveMethod()?.returnType as? PsiClassType)?.resolve()
      }
      return when (val ref = it.reference?.resolve()) {
        is PsiVariable -> (ref.type as? PsiClassType)?.resolve()
        is PsiClass -> ref
        else -> null
      }
    }
    return null
  }

  private fun getQualifierExpression(parameters: CompletionParameters): PsiExpression? {
    return (parameters.position.context as? PsiReferenceExpression)?.qualifierExpression
  }
}