// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * The MCP tools that the Markdown of a skill claims. Only an explicit claim counts:
 * - the command of an `execute_tool` call: `execute_tool(command="name …")`, with either quote style, or with `:`
 *   instead of `=`;
 * - the `"command"` value of a JSON router call: `{"tool":"execute_tool","arguments":{"command":"name …"}}`;
 * - a `### \`name\`` heading in a tool reference, also `### \`a\` / \`b\`` for tools that share one description;
 * - a `### Title — \`name\`` heading;
 * - a link to a tool page: `[name](tools/name.md)`, as in a generated `reference/tools.md` index;
 * - a name in the `allowed-tools` frontmatter: a value separated by spaces or commas, a `[a, b]` flow sequence, or a
 *   block sequence with or without an indent. A name can have quotes.
 *
 * An `allowed-tools` value in another form fails with an error. Otherwise a claim in that form is lost without a sign.
 *
 * A scan of every backticked token reports result codes such as `selection_required` as tools, and a skill is
 * supposed to use those words. A placeholder such as `<tool>` does not count, because a name starts with a letter.
 *
 * In `allowed-tools`, only a name that starts with a lowercase letter counts, and `execute_tool` does not count. The
 * harness tools of an agent, such as `Read` or `Bash`, are capitalized, and the router tool is not a tool of a skill.
 * An `mcp__<server>__<tool>` name does not count, because it can name a tool of another MCP server.
 *
 * A fenced code block is scanned like any other line, because a claim inside one is still a claim the agent will copy.
 */
@ApiStatus.Internal
object SkillToolClaims {
  /** The claim of [tool] in the Markdown [file] of a skill. */
  data class SkillToolClaim(val file: Path, val tool: String)

  private const val TOOL_NAME = "[A-Za-z][A-Za-z0-9_]*"
  private const val ROUTER_TOOL_NAME = "execute_tool"
  private val TOOL_HEADING = Regex("^###\\s+`$TOOL_NAME`(\\s*/\\s*`$TOOL_NAME`)*$")
  private val TITLED_TOOL_HEADING = Regex("^###\\s+[^`]+—\\s*`($TOOL_NAME)`$")
  private val TOOL_NAME_IN_HEADING = Regex("`($TOOL_NAME)`")
  private val EXECUTE_TOOL_COMMAND = Regex("$ROUTER_TOOL_NAME\\(command\\s*[=:]\\s*[\"']($TOOL_NAME)")
  private val JSON_COMMAND = Regex("\"command\"\\s*:\\s*\"($TOOL_NAME)")
  private val TOOL_PAGE_LINK = Regex("]\\(tools/($TOOL_NAME)\\.md\\)")
  private val ALLOWED_TOOLS = Regex("^allowed-tools:(.*)$")
  private val YAML_LIST_ITEM = Regex("^\\s*-\\s+(.+?)\\s*$")
  private val YAML_KEY = Regex("^[A-Za-z][A-Za-z0-9_-]*:.*$")
  private val ALLOWED_TOOL_NAME = Regex("[a-z][A-Za-z0-9_]*")
  private const val FRONTMATTER_DELIMITER = "---"

  fun toolClaims(file: Path): Set<String> = toolClaims(Files.readAllLines(file), source = file.toString())

  /** The claims in [lines]. [source] names the text in an error. */
  fun toolClaims(lines: List<String>, source: String): Set<String> {
    val claims = mutableSetOf<String>()
    allowedTools(lines, source).filterTo(claims) { name -> ALLOWED_TOOL_NAME.matches(name) && name != ROUTER_TOOL_NAME }
    for (line in lines) {
      val heading = line.trim()
      if (TOOL_HEADING.matches(heading)) TOOL_NAME_IN_HEADING.findAll(heading).mapTo(claims) { it.groupValues[1] }
      TITLED_TOOL_HEADING.matchEntire(heading)?.let { claims += it.groupValues[1] }
      EXECUTE_TOOL_COMMAND.findAll(line).mapTo(claims) { it.groupValues[1] }
      JSON_COMMAND.findAll(line).mapTo(claims) { it.groupValues[1] }
      TOOL_PAGE_LINK.findAll(line).mapTo(claims) { it.groupValues[1] }
    }
    return claims
  }

  /** Every claim in the Markdown files under [skillDirectory], in the order of [markdownFiles]. */
  fun claims(skillDirectory: Path): List<SkillToolClaim> {
    return markdownFiles(skillDirectory).flatMap { file -> toolClaims(file).map { tool -> SkillToolClaim(file, tool) } }
  }

  fun markdownFiles(directory: Path): List<Path> {
    return Files.walk(directory).use { paths ->
      paths.filter(Files::isRegularFile).filter { it.fileName.toString().endsWith(".md") }.sorted().toList()
    }
  }

  /** The values of the `allowed-tools` key, when [lines] start with a frontmatter block. */
  private fun allowedTools(lines: List<String>, source: String): List<String> {
    if (lines.firstOrNull()?.trim() != FRONTMATTER_DELIMITER) return emptyList()
    val frontmatter = lines.drop(1).takeWhile { it.trim() != FRONTMATTER_DELIMITER }
    val keyIndex = frontmatter.indexOfFirst { ALLOWED_TOOLS.matches(it) }
    if (keyIndex < 0) return emptyList()

    val inlineValue = ALLOWED_TOOLS.matchEntire(frontmatter[keyIndex])!!.groupValues[1].trim()
    val values = when {
      inlineValue.startsWith('[') && inlineValue.endsWith(']') -> inlineValue.substring(1, inlineValue.length - 1).split(',')
      inlineValue.isNotEmpty() -> {
        // A block scalar, a mapping, an anchor, a tag, a comment, or a flow sequence over more than one line.
        check(inlineValue.first() !in "[{|>&*!#") { unreadableAllowedTools(source, frontmatter[keyIndex]) }
        inlineValue.split(' ', ',')
      }
      else -> {
        val rest = frontmatter.drop(keyIndex + 1).filterNot(String::isBlank)
        val items = rest.takeWhile { YAML_LIST_ITEM.matches(it) }
        val next = rest.getOrNull(items.size)
        check(items.isNotEmpty() && (next == null || YAML_KEY.matches(next))) {
          unreadableAllowedTools(source, next ?: frontmatter[keyIndex])
        }
        items.map { YAML_LIST_ITEM.matchEntire(it)!!.groupValues[1] }
      }
    }
    return values.map(String::trim).filter(String::isNotEmpty).map(::unquoted)
  }

  private fun unquoted(value: String): String {
    val quoted = value.length >= 2 && value.first() in "\"'" && value.last() == value.first()
    return if (quoted) value.substring(1, value.length - 1) else value
  }

  private fun unreadableAllowedTools(source: String, line: String): String {
    return "SkillToolClaims cannot read the allowed-tools value of $source at '$line'. " +
           "Write the value in a form that SkillToolClaims documents, or teach SkillToolClaims this form."
  }
}
