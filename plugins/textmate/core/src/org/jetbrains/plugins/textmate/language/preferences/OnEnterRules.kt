package org.jetbrains.plugins.textmate.language.preferences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.textmate.bundles.TextRuleDeserializer


@Serializable
enum class IndentAction {
  @SerialName("none")
  NONE,

  @SerialName("indent")
  INDENT,

  @Suppress("unused")
  @SerialName("outdent")
  OUTDENT,

  @Suppress("unused")
  @SerialName("indentOutdent")
  INDENT_OUTDENT
}

@Serializable
data class OnEnterRule(
  val beforeText: TextRule,
  val afterText: TextRule? = null,
  val previousLineText: TextRule? = null,
  val action: Action
)

@Serializable
data class Action(
  val indent: IndentAction,
  val appendText: String? = null,
  val removeText: Int? = null
)

@Serializable(with = TextRuleDeserializer::class)
data class TextRule(val text: String)