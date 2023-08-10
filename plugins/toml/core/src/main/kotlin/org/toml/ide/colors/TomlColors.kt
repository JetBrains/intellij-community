/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.colors

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.util.NlsContexts
import org.toml.TomlBundle
import java.util.function.Supplier
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

enum class TomlColor(humanName: Supplier<@NlsContexts.AttributeDescriptor String>, default: TextAttributesKey? = null) {
    KEY(TomlBundle.messagePointer("color.settings.toml.keys"), Default.KEYWORD),
    COMMENT(TomlBundle.messagePointer("color.settings.toml.comments"), Default.LINE_COMMENT),
    BOOLEAN(TomlBundle.messagePointer("color.settings.toml.boolean"), Default.PREDEFINED_SYMBOL),
    NUMBER(OptionsBundle.messagePointer("options.language.defaults.number"), Default.NUMBER),
    DATE(TomlBundle.messagePointer("color.settings.toml.date"), Default.PREDEFINED_SYMBOL),
    STRING(OptionsBundle.messagePointer("options.language.defaults.string"), Default.STRING),
    VALID_STRING_ESCAPE(OptionsBundle.messagePointer("options.language.defaults.valid.esc.seq"), Default.VALID_STRING_ESCAPE),
    INVALID_STRING_ESCAPE(OptionsBundle.messagePointer("options.language.defaults.invalid.esc.seq"), Default.INVALID_STRING_ESCAPE),
    ;

    val textAttributesKey: TextAttributesKey = TextAttributesKey.createTextAttributesKey("org.toml.$name", default)
    val attributesDescriptor: AttributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
    val testSeverity: HighlightSeverity = HighlightSeverity(name, HighlightSeverity.INFORMATION.myVal)
}
