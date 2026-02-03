// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceCardsUsageCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HuggingFaceIdentifierPsiElement(
  private val parent: PsiElement,
  val entityName: String,
  val entityKind: HuggingFaceEntityKind
) : FakePsiElement() {

  init {
    parent.putUserData(HUGGING_FACE_ENTITY_NAME_KEY, true)
  }

  override fun getParent(): PsiElement = parent

  @NlsSafe
  fun stringValue(): String = entityName

  override fun navigate(requestFocus: Boolean) {
    when (entityKind) {
      HuggingFaceEntityKind.MODEL -> {
        val pipelineTag = HuggingFaceModelsCache.getPipelineTagForEntity(entityName) ?:
        HuggingFaceConstants.UNDEFINED_PIPELINE_TAG
        HuggingFaceCardsUsageCollector.NAVIGATION_LINK_IN_EDITOR_CLICKED.log(pipelineTag)
        BrowserUtil.browse(HuggingFaceURLProvider.getModelCardLink(entityName))
      }
      HuggingFaceEntityKind.DATASET -> {
        HuggingFaceCardsUsageCollector.NAVIGATION_LINK_IN_EDITOR_CLICKED.log(HuggingFaceConstants.DATASET_FAKE_PIPELINE_TAG)
        BrowserUtil.browse(HuggingFaceURLProvider.getDatasetCardLink(entityName))
      }
      HuggingFaceEntityKind.SPACE -> { }
    }
  }

  companion object {
    val HUGGING_FACE_ENTITY_NAME_KEY = Key.create<Boolean>("HUGGING_FACE_ENTITY_NAME_KEY")
  }
}
