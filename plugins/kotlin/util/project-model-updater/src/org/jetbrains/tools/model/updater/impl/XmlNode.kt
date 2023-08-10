// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater.impl

import org.jdom.Document
import org.jdom.Element
import org.jdom.output.Format
import java.io.File

fun xml(name: String, vararg args: Pair<String, Any>, block: XmlNode.() -> Unit = {}): XmlNode {
    return XmlNode(name, args.asList(), block)
}

class XmlNode(private val name: String, private val args: List<Pair<String, Any>>, block: XmlNode.() -> Unit = {}) {
    private val children = mutableListOf<XmlNode>()
    private var value: Any? = null

    init {
        @Suppress("UNUSED_EXPRESSION")
        block()
    }

    fun xml(name: String, vararg args: Pair<String, Any>?, block: XmlNode.() -> Unit = {}) {
        children += XmlNode(name, args.filterNotNull(), block = block)
    }

    fun raw(text: String) {
        value = text
    }

    private fun toElement(): Element {
        val element = Element(name)

        for (arg in args) {
            element.setAttribute(arg.first, arg.second.toString())
        }

        require(value == null || children.isEmpty())

        value?.let { value ->
            element.addContent(value.toString())
        }

        for (child in children) {
            element.addContent(child.toElement())
        }

        return element
    }

    fun render(addXmlDeclaration: Boolean): String {
        val document = Document()
        document.rootElement = toElement()

        val format = Format.getPrettyFormat().apply {
            omitDeclaration = !addXmlDeclaration
            lineSeparator = System.lineSeparator()
        }

        val output = @Suppress("DEPRECATION") org.jdom.output.XMLOutputter()
        output.format = format
        return output.outputString(document).trim()
    }
}

fun File.readXml(): Document {
    @Suppress("DEPRECATION")
    return org.jdom.input.SAXBuilder().build(this)
}