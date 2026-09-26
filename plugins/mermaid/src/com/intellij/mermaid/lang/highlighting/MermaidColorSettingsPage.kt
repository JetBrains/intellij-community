// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.highlighting

import com.intellij.mermaid.MermaidBundle.message
import com.intellij.mermaid.icons.MermaidIcons
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class MermaidColorSettingsPage : ColorSettingsPage {
    private companion object {
        val descriptors = arrayOf(
            AttributesDescriptor(message("color.diagram.name"), MermaidTextAttributes.diagram_name),
            AttributesDescriptor(message("color.keyword"), MermaidTextAttributes.keyword),
            AttributesDescriptor(message("color.identifier"), MermaidTextAttributes.identifier),
            AttributesDescriptor(message("color.arrow"), MermaidTextAttributes.edge),
            AttributesDescriptor(message("color.string"), MermaidTextAttributes.string),
            AttributesDescriptor(message("color.comment"), MermaidTextAttributes.comment),
            AttributesDescriptor(message("color.constant"), MermaidTextAttributes.constant),
            AttributesDescriptor(message("color.operator"), MermaidTextAttributes.operator),
            AttributesDescriptor(message("color.note"), MermaidTextAttributes.note),
            AttributesDescriptor(message("color.generic"), MermaidTextAttributes.generic),
            AttributesDescriptor(message("color.title"), MermaidTextAttributes.title),
            AttributesDescriptor(message("color.frontmatter.delimiter"), MermaidTextAttributes.frontmatter_delimiter),
        )

        val additionalTags = mapOf(
            "diagram_name" to MermaidTextAttributes.diagram_name,
            "keyword" to MermaidTextAttributes.keyword,
            "id" to MermaidTextAttributes.identifier,
            "arrow" to MermaidTextAttributes.edge,
            "string" to MermaidTextAttributes.string,
            "comment" to MermaidTextAttributes.comment,
            "constant" to MermaidTextAttributes.constant,
            "op" to MermaidTextAttributes.operator,
            "note" to MermaidTextAttributes.note,
            "generic" to MermaidTextAttributes.generic,
            "title" to MermaidTextAttributes.title,
            "t" to HighlighterColors.TEXT,
        )
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors

    override fun getColorDescriptors(): Array<ColorDescriptor> = emptyArray()

    override fun getDisplayName() = message("mermaid.colors.name")

    override fun getIcon() = MermaidIcons.MermaidFileType

    override fun getHighlighter() = MermaidHighlighter()

    override fun getDemoText() =
        checkNotNull(this::class.java.getResourceAsStream("sample.mermaid")?.bufferedReader()?.use { it.readText() })

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = additionalTags

}
