package org.jetbrains.plugins.textmate.language.preferences

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.jetbrains.plugins.textmate.bundles.TextRuleDeserializer


enum class IndentAction {
  @JsonProperty("none")
  NONE,

  @JsonProperty("indent")
  INDENT,

  @Suppress("unused")
  @JsonProperty("outdent")
  OUTDENT,

  @Suppress("unused")
  @JsonProperty("indentOutdent")
  INDENT_OUTDENT
}

data class OnEnterRule(
  val beforeText: TextRule,
  val afterText: TextRule?,
  val previousLineText: TextRule?,
  val action: Action
)

data class Action(
  val indent: IndentAction,
  val appendText: String?,
  val removeText: Int?
)

@JsonDeserialize(using = TextRuleDeserializer::class)
data class TextRule(
  val text: String
)