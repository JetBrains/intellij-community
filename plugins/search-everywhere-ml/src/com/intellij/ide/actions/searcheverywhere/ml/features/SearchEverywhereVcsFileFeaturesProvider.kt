package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiFileSystemItem

class SearchEverywhereVcsFileFeaturesProvider : SearchEverywhereElementFeaturesProvider(FileSearchEverywhereContributor::class.java) {
  companion object {
    private const val IS_IGNORED_DATA_KEY = "isIgnored"
    private const val IS_CHANGED_DATA_KEY = "isChanged"
    private const val FILE_STATUS_DATA_KEY = "fileStatus"
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    val item = when (element) {
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> (element.item as? PsiFileSystemItem) ?: return emptyMap()
      is PsiFileSystemItem -> element
      else -> return emptyMap()
    }

    return getFileFeatures(item)
  }

  private fun getFileFeatures(item: PsiFileSystemItem): Map<String, Any> {
    if (item.isDirectory) {
      return emptyMap()
    }

    val changeListManager = ChangeListManager.getInstance(item.project)

    return hashMapOf(
      IS_CHANGED_DATA_KEY to changeListManager.isFileAffected(item.virtualFile),
      IS_IGNORED_DATA_KEY to changeListManager.isIgnoredFile(item.virtualFile),
      FILE_STATUS_DATA_KEY to changeListManager.getStatus(item.virtualFile).id
    )
  }
}