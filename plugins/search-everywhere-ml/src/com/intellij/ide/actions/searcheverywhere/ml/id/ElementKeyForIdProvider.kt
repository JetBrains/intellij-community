package com.intellij.ide.actions.searcheverywhere.ml.id

import com.intellij.openapi.extensions.ExtensionPointName

internal abstract class ElementKeyForIdProvider {
  companion object {
    private val EP_NAME = ExtensionPointName.create<ElementKeyForIdProvider>("com.intellij.searcheverywhere.ml.elementKeyForIdProvider")

    fun isElementSupported(element: Any) = getKeyOrNull(element) != null

    /**
     * Returns key that will be used by [SearchEverywhereMlItemIdProvider].
     * The method may throw a [UnsupportedElementTypeException] if no provider was found for the element,
     * therefore [isElementSupported] should be used before to make sure that calling this method is safe.
     * @return Key for ID
     */
    fun getKey(element: Any) = getKeyOrNull(element) ?: throw UnsupportedElementTypeException(element::class.java)

    private fun getKeyOrNull(element: Any): Any? {
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
   * Returns a unique key that will be used by [SearchEverywhereMlItemIdProvider] to obtain
   * an id of the element.
   *
   * If the element type is not supported by the provider, it will return null.
   * @return Unique key based on the [element] or null if not supported
   */
  protected abstract fun getKey(element: Any): Any?
}

internal class UnsupportedElementTypeException(elementType: Class<*>): Exception("No provider found for element type: ${elementType}")
