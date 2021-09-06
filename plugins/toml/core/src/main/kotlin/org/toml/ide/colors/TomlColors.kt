/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.colors

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

enum class TomlColor(humanName: String, default: TextAttributesKey? = null) {
    KEY("Keys", Default.KEYWORD),
    COMMENT("Comments", Default.LINE_COMMENT),
    BOOLEAN("Boolean", Default.PREDEFINED_SYMBOL),
    NUMBER("Number", Default.NUMBER),
    DATE("Date", Default.PREDEFINED_SYMBOL),
    STRING("Strings//String text", Default.STRING),
    VALID_STRING_ESCAPE("Strings//Escape sequence//Valid", Default.VALID_STRING_ESCAPE),
    INVALID_STRING_ESCAPE("Strings//Escape sequence//Invalid", Default.INVALID_STRING_ESCAPE),
    ;

    val textAttributesKey: TextAttributesKey = TextAttributesKey.createTextAttributesKey("org.toml.$name", default)
    val attributesDescriptor: AttributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
    val testSeverity: HighlightSeverity = HighlightSeverity(name, HighlightSeverity.INFORMATION.myVal)
}
