package org.jetbrains.plugins.textmate.language.preferences

import kotlin.jvm.JvmStatic

data class IndentationRules(
  val increaseIndentPattern: String?,
  val decreaseIndentPattern: String?,
  val indentNextLinePattern: String?,
  val unIndentedLinePattern: String?
) {
  val isEmpty: Boolean
    get() = this.increaseIndentPattern == null && this.decreaseIndentPattern == null && this.indentNextLinePattern == null && this.unIndentedLinePattern == null

  fun updateWith(other: IndentationRules): IndentationRules {
    return IndentationRules(
      other.increaseIndentPattern ?: this.increaseIndentPattern,
      other.decreaseIndentPattern ?: this.decreaseIndentPattern,
      other.indentNextLinePattern ?: this.indentNextLinePattern,
      other.unIndentedLinePattern ?: this.unIndentedLinePattern
    )
  }

  companion object {
    @JvmStatic
    fun empty(): IndentationRules {
      return IndentationRules(null, null, null, null)
    }
  }
}