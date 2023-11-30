package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.openapi.extensions.ExtensionPointName

internal interface ElementKeyForIdProvider {
  companion object {
    internal val EP_NAME = ExtensionPointName.create<ElementKeyForIdProvider>("com.intellij.searcheverywhere.ml.elementKeyForIdProvider")

    /**
     * Returns key that will be used by [SearchEverywhereMlOrderedItemIdProvider].
     * The method returns an element key or null if element isn't supported yet.
     * @return Key for ID
     */
    fun getKeyOrNull(element: Any): Any? {
      EP_NAME.extensionList.forEach {
        val key = it.getKey(element)
        if (key != null) {
          return key
        }
      }

      return null
    }
  }

  /**
   * Returns a unique key that will be used by [SearchEverywhereMlOrderedItemIdProvider] to obtain
   * an id of the element.
   *
   * If the element type is not supported by the provider, it will return null.
   * @return Unique key based on the [element] or null if not supported
   */
  fun getKey(element: Any): Any?
}