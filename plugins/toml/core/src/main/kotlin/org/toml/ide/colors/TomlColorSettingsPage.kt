/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.io.StreamUtil
import org.toml.TomlIcons
import org.toml.ide.TomlHighlighter
import org.toml.lang.TomlLanguage
import javax.swing.Icon

class TomlColorSettingsPage : ColorSettingsPage {

    private val attributesDescriptors = TomlColor.values().map { it.attributesDescriptor }.toTypedArray()
    private val tagToDescriptorMap = TomlColor.values().associateBy({ it.name }, { it.textAttributesKey })
    private val highlighterDemoText by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("org/toml/ide/colors/highlighterDemoText.toml")
        StreamUtil.convertSeparators(StreamUtil.readText(stream, "UTF-8"))
    }

    override fun getDisplayName(): String = TomlLanguage.displayName
    override fun getHighlighter(): SyntaxHighlighter = TomlHighlighter()
    override fun getIcon(): Icon = TomlIcons.TomlFile
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tagToDescriptorMap
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = attributesDescriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDemoText(): String = highlighterDemoText
}
