package com.intellij.lang.properties.diff.data

/**
 * Represents the information about a property that can be retrieved without the access to PSI.
 * Properties are considered to be equal if their unescaped keys and values are equal. For example:
 * ```properties
 * \u0048\u0065\u006C\u006C\u006F = \u0048\u0065\u006C\u006C\u006F
 * ```
 * equals to
 * ```properties
 * Hello = Hello
 * ```
 * @property key - unescaped property key [com.intellij.lang.properties.IProperty.getUnescapedKey]
 * @property value - unescaped property value [com.intellij.lang.properties.IProperty.getUnescapedValue]
 * @property range - line range of the property in the one version of the file.
 */
internal class PropertyInfo(val key: String, val value: String, val range: SemiOpenLineRange)