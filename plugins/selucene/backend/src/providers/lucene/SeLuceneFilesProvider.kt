// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.lucene

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.psi.PsiManager
import com.intellij.selucene.common.SeLuceneProviderIdUtils
import org.jetbrains.annotations.Nls
import java.nio.file.Path

class SeLuceneFileItem(val file: VirtualFile, val name: String, val score: Float) : SeItem {
  override fun toString(): String = file.presentableName
  override fun weight(): Int {
    return (score * 1000).toInt()
  }

  override suspend fun presentation(): SeItemPresentation {
    return SeTargetItemPresentationBuilder()
      .withPresentableText(file.presentableName)
      .withContainerText(file.path)
      .withMultiSelectionSupported(false)
      .build()
  }
}

class SeLuceneFilesProvider(private val project: Project) : SeItemsProvider {
  override val id: String get() = SeLuceneProviderIdUtils.LUCENE_FILES
  override val displayName: @Nls String get() = "Lucene Files"

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    if (params.inputQuery.isEmpty()) return

    LuceneSearcher.search(params.inputQuery, project).collect {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of((it.path))) ?: return@collect
      collector.put(SeLuceneFileItem(virtualFile, it.name, it.score))
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