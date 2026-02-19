// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.ide.IdeBundle
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceIdentifierPsiElement
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceApi.fetchOrRetrieveModelCard
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceDatasetsCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache
import com.intellij.python.community.impl.huggingFace.icons.PythonCommunityImplHuggingFaceIcons
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceCardsUsageCollector
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import com.jetbrains.python.psi.PyTargetExpression

internal class HuggingFaceDocumentationTarget(private val myElement : PsiElement) : DocumentationTarget {

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val psiElementPointer = myElement.createSmartPointer()
    return Pointer<DocumentationTarget> { psiElementPointer.element?.let { HuggingFaceDocumentationTarget(it) } }
  }

  override fun computePresentation(): TargetPresentation {
    val elementText = when (myElement) {
      is PyTargetExpression -> HuggingFaceUtil.extractTextFromPyTargetExpression(myElement)
      is HuggingFaceIdentifierPsiElement -> myElement.stringValue()
      else -> PyHuggingFaceBundle.message("python.hugging.face.unknown.element")
    }
    return TargetPresentation.builder(elementText)
      .icon(PythonCommunityImplHuggingFaceIcons.Logo)
      .presentation()
  }

  override fun computeDocumentationHint(): String = IdeBundle.message("open.url.in.browser.tooltip")

  override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation {
    val (entityId, entityKind) = HuggingFaceUtil.extractStringValueAndEntityKindFromElement(myElement)
                                 ?: return@asyncDocumentation DocumentationResult.documentation(PyHuggingFaceBundle.message("python.hugging.face.no.string.value.found"))

    val entityDataApiContent = when (entityKind) {
      HuggingFaceEntityKind.MODEL -> HuggingFaceModelsCache.getBasicData(entityId)
      HuggingFaceEntityKind.DATASET -> HuggingFaceDatasetsCache.getBasicData(entityId)
      HuggingFaceEntityKind.SPACE -> return@asyncDocumentation DocumentationResult.documentation(PyHuggingFaceBundle.message("python.hugging.face.spaces.documentation.not.supported"))
    }

    if (entityDataApiContent == null) return@asyncDocumentation DocumentationResult.documentation(PyHuggingFaceBundle.message("python.hugging.face.could.not.fetch"))
    val modelCardContent = fetchOrRetrieveModelCard(entityDataApiContent, entityId, entityKind)
    val project = readAction { myElement.project }

    val pipelineTag = when (entityKind) {
      HuggingFaceEntityKind.MODEL -> HuggingFaceModelsCache.getPipelineTagForEntity(entityId) ?: HuggingFaceConstants.UNDEFINED_PIPELINE_TAG
      HuggingFaceEntityKind.DATASET -> HuggingFaceConstants.DATASET_FAKE_PIPELINE_TAG
      HuggingFaceEntityKind.SPACE -> HuggingFaceConstants.SPACE_FAKE_PIPELINE_TAG
    }

    HuggingFaceCardsUsageCollector.CARD_SHOWN_ON_HOVER.log(pipelineTag)
    val htmlContent = HuggingFaceHtmlBuilder(
      project,
      entityDataApiContent,
      modelCardContent,
      entityKind
    ).build()
    DocumentationResult.documentation(html = htmlContent)
  }
}
