// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.files

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.selucene.common.SeLuceneProviderIdUtils
import com.intellij.util.text.matching.MatchingMode
import org.jetbrains.annotations.Nls
import java.nio.file.Path

class SeLuceneFileItem(
  private val project: Project,
  val file: VirtualFile,
  val name: String,
  val score: Float,
  private val inputQuery: String,
  private val fileModel: GotoFileModel,
  private val presentationRenderer: SearchEverywherePsiRenderer,
) : SeItem {
  override fun toString(): String = file.presentableName
  override fun weight(): Int = (score * 1000).toInt()

  override suspend fun presentation(): SeItemPresentation = readAction {
    val psi = PsiManager.getInstance(project).findFile(file)
    val targetPresentation = buildTargetPresentation(psi)
    val matchers = (psi as? PsiFileSystemItem)?.let(::buildFileItemMatchers)

    SeTargetItemPresentationBuilder()
      .withTargetPresentation(
        tp = targetPresentation,
        matchers = matchers,
        extendedInfo = null,
        isMultiSelectionSupported = false
      )
      .build()
  }

  private fun buildTargetPresentation(psi: PsiFile?): TargetPresentation {
    return if (psi != null) {
      presentationRenderer.computePresentation(psi)
    }
    else {
      TargetPresentation.builder(file.presentableName)
        .icon(file.fileType.icon)
        .presentation()
    }
  }

  private fun buildFileItemMatchers(psi: PsiFileSystemItem): ItemMatchers {
    val matcher = NameUtil.buildMatcherWithFallback("*$inputQuery", "*$inputQuery", MatchingMode.IGNORE_CASE)
    val matchers = ItemMatchers(matcher, null)

    return GotoFileModel.convertToFileItemMatchers(matchers, psi, fileModel)
  }
}

class SeLuceneFilesProvider(private val project: Project) : SeItemsProvider {
  override val id: String get() = SeLuceneProviderIdUtils.LUCENE_FILES
  override val displayName: @Nls String get() = "Lucene Files"

  private val fileModel = GotoFileModel(project)
  private val presentationRenderer = SearchEverywherePsiRenderer(this)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    if (params.inputQuery.isEmpty()) return

    FileIndex.getInstance(project)
      .search(params)
      .collect {
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of((it.path))) ?: return@collect
        collector.put(SeLuceneFileItem(project, virtualFile, it.name, it.score, params.inputQuery, fileModel, presentationRenderer))
      }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val file = (item as? SeLuceneFileItem)?.file ?: return false
    val psi = readAction {
      PsiManager.getInstance(project).findFile(file)
    }

    psi?.navigate(false) ?: FileEditorManager.getInstance(project).openFile(file, true)

    return true
  }

  override suspend fun canBeShownInFindResults(): Boolean = true

  override fun dispose() {}


}