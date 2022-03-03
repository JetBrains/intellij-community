// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.xml

open class XmlBuilder {
  private val version: String = "1.0"
  private val encoding: String = "UTF-8"

  private var indent: Int = 0

  private val state = StringBuilder()

  private val attributes = LinkedHashMap<String, String>()

  fun attribute(name: String, value: String) {
    attributes[name] = """"$value""""
  }

  private fun createTagWithParametersAndReset(tag: String): String {
    if (attributes.isEmpty()) return tag
    val rawAttributes = attributes.map { (name, value) -> "$name=$value" }.joinToString(" ")
    attributes.clear()
    return "$tag $rawAttributes"
  }

  fun block(tag: String, action: () -> Unit) {
    val begin = createTagWithParametersAndReset(tag)
    text("<$begin>")
    indent++
    action()
    indent--
    text("</$tag>")
  }

  fun value(tag: String, value: Any?) {
    val begin = createTagWithParametersAndReset(tag)
    text("<$begin>$value</$tag>")
  }

  fun text(text: String) {
    state.appendln("  ".repeat(indent) + text)
  }

  open fun generate(): String {
    return "<?xml version=\"$version\" encoding=\"$encoding\"?>\n$state"
  }
}
