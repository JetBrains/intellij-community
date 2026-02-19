package com.intellij.lang.properties.diff.data

import com.intellij.diff.tools.util.text.LineOffsets

/**
 * Stores information about a file needed for the properties diff algorithm.
 */
internal data class FileInfo(val fileText: CharSequence, val lineOffsets: LineOffsets, val propertyInfoMap: Map<String, PropertyInfo>)