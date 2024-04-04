// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.ide.IdeBundle
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceDatasetPsiElement
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceModelPsiElement
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceEntityBasicApiData
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceHttpClient
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceDatasetsCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceMdCacheEntry
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceMdCardsCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceCardsUsageCollector
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import com.intellij.refactoring.suggested.createSmartPointer
import com.jetbrains.python.psi.PyTargetExpression
import java.time.Instant

internal class HuggingFaceDocumentationTarget(private val myElement : PsiElement) : DocumentationTarget {

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val psiElementPointer = myElement.createSmartPointer()

    return object : Pointer<DocumentationTarget> {
      override fun dereference(): DocumentationTarget? {
        return psiElementPointer.element?.let { HuggingFaceDocumentationTarget(it) }
      }

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as HuggingFaceDocumentationTarget
        return myElement == other.myElement
      }

      override fun hashCode(): Int {
        return myElement.hashCode()
      }
    }
  }

  override fun computePresentation(): TargetPresentation {
    val elementText = when (myElement) {
      is PyTargetExpression -> "${HuggingFaceConstants.HF_EMOJI} ${HuggingFaceUtil.extractTextFromPyTargetExpression(myElement)}"
      is HuggingFaceModelPsiElement -> "${HuggingFaceConstants.HF_EMOJI}  ${myElement.stringValue()}"
      is HuggingFaceDatasetPsiElement -> "${HuggingFaceConstants.HF_EMOJI}  ${myElement.stringValue()}"
      else -> PyHuggingFaceBundle.message("python.hugging.face.unknown.element")
    }
    return TargetPresentation.builder(elementText).icon(null).presentation()
  }

  override fun computeDocumentationHint(): String = IdeBundle.message("open.url.in.browser.tooltip")

  override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation {
    val (entityId, entityKind) = HuggingFaceUtil.extractStringValueAndEntityKindFromElement(myElement)
    if (entityId == null || entityKind == null) {
      return@asyncDocumentation DocumentationResult.documentation(PyHuggingFaceBundle.message("python.hugging.face.no.string.value.found"))
    }

    try {
      val entityDataApiContent = if (entityKind == HuggingFaceEntityKind.MODEL) {
        HuggingFaceModelsCache.getBasicData(entityId)
      } else {
        HuggingFaceDatasetsCache.getBasicData(entityId)
      }
      // shall we log failures?
      if (entityDataApiContent == null) { return@asyncDocumentation DocumentationResult.documentation(PyHuggingFaceBundle.message("python.hugging.face.could.not.fetch")) }
      val modelCardContent = fetchOrRetrieveModelCard(entityDataApiContent, entityId, entityKind)
      val project = readAction { myElement.project }

      val pipelineTag = when (entityKind) {
        HuggingFaceEntityKind.MODEL -> HuggingFaceModelsCache.getPipelineTagForEntity(entityId) ?: HuggingFaceConstants.UNDEFINED_PIPELINE_TAG
        HuggingFaceEntityKind.DATASET -> HuggingFaceConstants.DATASET_FAKE_PIPELINE_TAG
      }
      HuggingFaceCardsUsageCollector.CARD_SHOWN_ON_HOVER.log(pipelineTag)

      val htmlContent = HuggingFaceHtmlBuilder(
        project,
        entityDataApiContent,
        modelCardContent,
        entityKind
      ).build()
      DocumentationResult.documentation(html = htmlContent)
    } catch (e: Exception) {
      e.printStackTrace()
      DocumentationResult.documentation(PyHuggingFaceBundle.message("python.hugging.face.not.found", e.message ?: ""))
    }
  }

  private fun fetchOrRetrieveModelCard(entityDataApiContent: HuggingFaceEntityBasicApiData,
                                       entityId: String,
                                       entityKind: HuggingFaceEntityKind): String {
    return if (entityDataApiContent.gated != "false") {
      HuggingFaceDocumentationPlaceholdersUtil.generateGatedEntityMarkdownString(entityId, entityKind)
    } else {
      val cached = HuggingFaceMdCardsCache.getData("markdown_$entityId")
      cached?.data
      ?: try {
        val mdUrl = HuggingFaceURLProvider.getEntityMarkdownURL(entityId, entityKind).toString()
        val rawData = HuggingFaceHttpClient.downloadFile(mdUrl)
                             ?: HuggingFaceDocumentationPlaceholdersUtil.noInternetConnectionPlaceholder(entityId)
        val cleanedData = HuggingFaceReadmeCleaner(rawData, entityId, entityKind).doCleanUp().getMarkdown()
        HuggingFaceMdCardsCache.saveData("markdown_$entityId", HuggingFaceMdCacheEntry(cleanedData, Instant.now()))
        cleanedData
      } catch (e: Exception) {
        HuggingFaceDocumentationPlaceholdersUtil.noInternetConnectionPlaceholder(entityId)
      }
    }
  }
}
