// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.elicitation

import com.intellij.lang.Language
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Code
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.BOLD
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.ITALIC
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.UNDERLINE
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Styled
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Text
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.TextColor.GREEN
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.TextColor.RED
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.TextColor.YELLOW
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.Font as AwtFont


/**
 * Renders message [parts] into one ANSI string for a terminal.
 * Parts are joined with no separator, so put line breaks inside [ElicitationMessagePart.Text].
 * [ElicitationMessagePart.Code] is syntax-highlighted; [ElicitationMessagePart.Styled] gets emphasis.
 * Removing the ANSI escapes gives back the original text.
 */
fun renderToAnsi(parts: List<ElicitationMessagePart>, project: Project? = null): String = buildString {
  for (part in parts) {
    when (part) {
      is Text -> append(part.text)
      is Code -> append(highlightToAnsi(part.language, part.text, project))
      is Styled -> append(renderStyled(part))
    }
  }
}

/**
 * Renders [code] of the given [language] into an ANSI string for a terminal,
 * using the IDE syntax highlighter and the global color scheme.
 *
 * The highlighter is found at runtime by [SyntaxHighlighterFactory]. If the [language] has none,
 * a plain highlighter is used, so the text stays uncolored instead of failing.
 * Token text is copied as is, so removing the ANSI escapes gives back [code].
 *
 * [project] is only needed by languages whose lexer reads project state. SQL does not, so it may be
 * `null`; the CLI path still passes it in case some other language needs it.
 */
private fun highlightToAnsi(language: Language, code: CharSequence, project: Project? = null): String {
  val scheme = EditorColorsUtil.getGlobalOrDefaultColorScheme()
  val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, null)
  val lexer = highlighter.highlightingLexer

  val text = code.toString()
  val sb = StringBuilder(text.length)
  lexer.start(text)
  while (lexer.tokenType != null) {
    val tokenText = text.substring(lexer.tokenStart, lexer.tokenEnd)
    // getTokenHighlights returns keys from least to most specific; merge them so the most specific wins
    // (mirrors the editor's LayeredTextAttributes). Taking only the first key would drop overrides.
    val attributes = highlighter.getTokenHighlights(lexer.tokenType)
      .fold(null as TextAttributes?) { acc, key ->
        val a = scheme.getAttributes(key) ?: return@fold acc
        if (acc == null) a else TextAttributes.merge(acc, a)
      }
    sb.appendStyled(attributes, tokenText)
    lexer.advance()
  }
  return sb.toString()
}

/**
 * Renders one [ElicitationMessagePart.Styled] into ANSI.
 * [ElicitationMessagePart.Styled.color] becomes a basic ANSI color (the terminal picks the real shade);
 * [ElicitationMessagePart.Styled.styles] add bold/italic/underline.
 * If anything is set, the text is wrapped with a reset at the end; otherwise it is returned as is.
 */
private fun renderStyled(part: Styled): String {
  val sgr = buildString {
    part.color?.let { append("$ESC[${it.sgrCode}m") }
    if (BOLD in part.styles) append("$ESC[${SGR_BOLD}m")
    if (ITALIC in part.styles) append("$ESC[${SGR_ITALIC}m")
    if (UNDERLINE in part.styles) append("$ESC[${SGR_UNDERLINE}m")
  }
  return if (sgr.isEmpty()) part.text else sgr + part.text + RESET
}

private fun StringBuilder.appendStyled(attributes: TextAttributes?, text: String) {
  val sgr = attributes?.toSgr()
  if (sgr.isNullOrEmpty()) {
    append(text)
    return
  }
  append(sgr).append(text).append(RESET)
}

/**
 * Builds the opening SGR codes for the foreground, background and bold/italic font of these attributes,
 * or an empty string if none are set. Other effects (underline, strikethrough, ...) are ignored.
 */
private fun TextAttributes.toSgr(): String = buildString {
  foregroundColor?.let { append(it.sgr(SGR_FOREGROUND)) }
  backgroundColor?.let { append(it.sgr(SGR_BACKGROUND)) }
  // font type is a bitmask
  if (fontType and AwtFont.BOLD != 0) append("$ESC[${SGR_BOLD}m")
  if (fontType and AwtFont.ITALIC != 0) append("$ESC[${SGR_ITALIC}m")
}

/**
 * 24-bit true color SGR. [layer] is [SGR_FOREGROUND] or [SGR_BACKGROUND].
 * Terminals without 24-bit color may drop or approximate it (no 8/256-color fallback).
 * Target CLI agents support it. Colors that are using basic ANSI codes and are not affected.
 */
private fun Color.sgr(layer: Int): String = "$ESC[$layer;$SGR_TRUE_COLOR;$red;$green;${blue}m"

/** ANSI SGR (Select Graphic Rendition) codes. */
internal const val SGR_RESET = 0          // reset all styling
internal const val SGR_BOLD = 1
internal const val SGR_ITALIC = 3
internal const val SGR_UNDERLINE = 4
internal const val SGR_TRUE_COLOR = 2     // sub-selector for 24-bit color: "<layer>;2;r;g;b"
internal const val SGR_FOREGROUND = 38    // layer selector: set foreground color
internal const val SGR_BACKGROUND = 48    // layer selector: set background color

/** Basic ANSI foreground color codes (the terminal maps them to its palette). */
internal const val SGR_FG_RED = 31
internal const val SGR_FG_GREEN = 32
internal const val SGR_FG_YELLOW = 33

/** ANSI escape character that starts every SGR sequence (`ESC[...m`). */
internal const val ESC = "\u001B"
/** SGR sequence that resets all styling; appended after every styled span. */
internal const val RESET = "$ESC[${SGR_RESET}m"

/** Basic ANSI foreground code for this color. Lives in the renderer so [ElicitationMessagePart.TextColor] stays ANSI-free. */
internal val ElicitationMessagePart.TextColor.sgrCode: Int
  get() = when (this) {
    RED -> SGR_FG_RED
    GREEN -> SGR_FG_GREEN
    YELLOW -> SGR_FG_YELLOW
  }
