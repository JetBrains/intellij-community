// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.properties

import org.jetbrains.annotations.Contract

class PropertyData(val name: String, val value: PropertyValue)

class PropertiesMap : HashMap<String, PropertyValue?>()

/**
 * TODO: Add correct support of binary properties - support in api, diff, etc.
 */
data class PropertyValue(private val value: String) {
  override fun toString(): String = value

  companion object {
    @JvmStatic
    @Contract(value = "null -> null; !null -> !null", pure = true)
    fun create(propertyValue: String?): PropertyValue? = propertyValue?.let { PropertyValue(it) }

    @JvmStatic
    @Contract(value = "null -> null; !null -> !null", pure = true)
    fun toString(value: PropertyValue?): String? = value?.toString()
  }
}
