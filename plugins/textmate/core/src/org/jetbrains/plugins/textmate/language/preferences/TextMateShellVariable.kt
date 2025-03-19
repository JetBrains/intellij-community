package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner

data class TextMateShellVariable(override val scopeSelector: CharSequence, val name: String, val value: String) : TextMateScopeSelectorOwner
