package com.intellij.lang.properties.merge.data

import com.intellij.psi.PsiElement

/**
 * This class stores information about a property which is affected by some conflict.
 *
 * @property borderElement - next element, after the previous property in the file  (see [com.intellij.lang.properties.psi.impl.PropertyImpl.getEdgeOfProperty])
 * @property key - property key
 * @property value - property value
 * @property comment - comment that is located directly above property, for example, for
 * ```properties
 * # zero line
 *
 * # first line
 * # second line
 * key=value
 * ```
 * comment is "# first line\n# second line"
 */
internal data class PropertyInfo(val borderElement: PsiElement, val key: String, val value: String, val comment: String?)