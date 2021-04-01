package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.font.TextAttribute
import javax.swing.JLabel

@Suppress("UNCHECKED_CAST") // Required to make the typesystem happy
internal fun JLabel.setUnderlined() {
    val attributes = font.attributes as MutableMap<TextAttribute, Any>
    attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
    font = font.deriveFont(attributes)
}
