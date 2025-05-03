package com.intellij.lang.properties.diff.data

import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * Stores information about a file needed for the diff algorithm.
 *
 * @property lineFragmentRangeList - Each [com.intellij.diff.fragments.LineFragment] consists of two parts of changes (before and after).
 * This field stores list of [TextRange] of the fragment side to which this file corresponds.
 */
internal data class FileInfo(val file: PsiFile, val fileText: CharSequence, val lineOffsets: LineOffsets, val lineFragmentRangeList: List<TextRange>)