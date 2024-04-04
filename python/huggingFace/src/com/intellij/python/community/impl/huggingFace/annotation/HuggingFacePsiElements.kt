// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceCardsUsageCollector


abstract class HuggingFaceEntityPsiElement(
  private val parent: PsiElement,
  private val entityName: String
) : FakePsiElement() {
  init {
    parent.putUserData(HUGGING_FACE_ENTITY_NAME_KEY, true)
  }

  override fun getParent(): PsiElement = parent

  @NlsSafe
  fun stringValue(): String = entityName

  companion object {
    val HUGGING_FACE_ENTITY_NAME_KEY = Key.create<Boolean>("HUGGING_FACE_ENTITY_NAME_KEY")
  }
}

class HuggingFaceModelPsiElement(parent: PsiElement, private val modelName: String
) : HuggingFaceEntityPsiElement(parent, modelName) {
  override fun navigate(requestFocus: Boolean) {
    val pipelineTag = HuggingFaceModelsCache.getPipelineTagForEntity(modelName) ?: HuggingFaceConstants.UNDEFINED_PIPELINE_TAG
    HuggingFaceCardsUsageCollector.NAVIGATION_LINK_IN_EDITOR_CLICKED.log(pipelineTag)
    BrowserUtil.browse(HuggingFaceURLProvider.getModelCardLink(modelName))
  }
}

class HuggingFaceDatasetPsiElement(parent: PsiElement, private val datasetName: String
) : HuggingFaceEntityPsiElement(parent, datasetName) {
  override fun navigate(requestFocus: Boolean) {
    HuggingFaceCardsUsageCollector.NAVIGATION_LINK_IN_EDITOR_CLICKED.log(HuggingFaceConstants.DATASET_FAKE_PIPELINE_TAG)
    BrowserUtil.browse(HuggingFaceURLProvider.getDatasetCardLink(datasetName))
  }
}
