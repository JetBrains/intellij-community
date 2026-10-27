// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil

/**
 * A layout node in the output of [PyTypeRenderer].
 *
 * Each node is an [HtmlChunk] that prints its flat form. Thus a caller that only calls [HtmlChunk.toString] gets the type on one line.
 * [layout] breaks the lines of a rendered type so that the type fits in a given width.
 * Any other [HtmlChunk] in the tree is a leaf that [layout] never breaks.
 */
internal sealed class PyTypeLayout : HtmlChunk() {
  private class Concat(val parts: List<HtmlChunk>) : PyTypeLayout()

  /**
   * Prints [content] flat when it fits in the rest of the line, and breaks each [Line] that it owns otherwise.
   * A group that starts past the width stays flat, because its first line overflows anyway.
   */
  private class Group(val content: HtmlChunk) : PyTypeLayout()

  /** Indents the lines that start inside [content] by [INDENT_SIZE] more columns. */
  private class Indent(val content: HtmlChunk) : PyTypeLayout()

  /** Prints [flat] in a flat group. In a broken group, prints [beforeBreak], a line break, the indentation, and then [afterBreak]. */
  private class Line(val flat: HtmlChunk, val beforeBreak: HtmlChunk, val afterBreak: HtmlChunk) : PyTypeLayout()

  /**
   * Puts as many items on a line as fit, and breaks only the separators that it must break.
   * The [parts] are the items, with a separator [Line] between each two items.
   */
  private class Fill(val parts: List<HtmlChunk>) : PyTypeLayout()

  override fun appendTo(builder: StringBuilder) {
    when (this) {
      is Concat -> parts.forEach { it.appendTo(builder) }
      is Group -> content.appendTo(builder)
      is Indent -> content.appendTo(builder)
      is Line -> flat.appendTo(builder)
      is Fill -> parts.forEach { it.appendTo(builder) }
    }
  }

  override fun isEmpty(): Boolean = when (this) {
    is Concat -> parts.all { it.isEmpty }
    is Group -> content.isEmpty
    is Indent -> content.isEmpty
    is Line -> false
    is Fill -> parts.all { it.isEmpty }
  }

  companion object {
    private const val INDENT_SIZE = 4

    @JvmStatic
    fun concat(vararg parts: HtmlChunk): HtmlChunk = Concat(parts.toList())

    @JvmStatic
    fun concat(parts: List<HtmlChunk>): HtmlChunk = Concat(parts)

    /**
     * A separator that prints [flat] on one line.
     * When the separator breaks, the line ends with [beforeBreak], and the next line starts with [afterBreak].
     */
    @JvmStatic
    fun separator(flat: HtmlChunk, beforeBreak: HtmlChunk, afterBreak: HtmlChunk): HtmlChunk = Line(flat, beforeBreak, afterBreak)

    /** Joins [items] with [separator], and breaks only the separators that must break. The next lines get one more indent. */
    @JvmStatic
    fun fill(items: List<HtmlChunk>, separator: HtmlChunk): HtmlChunk = Indent(Fill(interleave(items, separator)))

    /**
     * Puts [items] between [open] and [close].
     *
     * When the items do not fit on one line, each item goes on its own indented line, and the last item gets [trailingComma].
     * One item stays next to the brackets, so `list[dict[str, int]]` breaks inside `dict[...]` only.
     * With [fill], the items fill each line instead of one item for each line.
     */
    @JvmStatic
    @JvmOverloads
    fun bracketed(
      open: HtmlChunk,
      items: List<HtmlChunk>,
      separator: HtmlChunk,
      trailingComma: HtmlChunk,
      close: HtmlChunk,
      fill: Boolean = false,
    ): HtmlChunk = when (items.size) {
      0 -> Concat(listOf(open, close))
      1 -> Concat(listOf(open, items[0], close))
      else -> {
        val content = if (fill) Fill(interleave(items, separator)) else Concat(interleave(items, separator))
        val softLine = Line(empty(), empty(), empty())
        val closingLine = Line(empty(), trailingComma, empty())
        Group(Concat(listOf(open, Indent(Concat(listOf(softLine, content))), closingLine, close)))
      }
    }

    /**
     * Prints [chunk] with its lines broken to fit in [width] columns.
     *
     * The first line starts at [startColumn]. A new line starts at [indent] columns, plus the indentation of its nesting level.
     * The line break is `\n`, and the indentation is spaces.
     * Set [html] when the leaves of [chunk] are HTML, so that the tags and the entities do not count as columns.
     */
    @JvmStatic
    fun layout(chunk: HtmlChunk, width: Int, startColumn: Int, indent: Int, html: Boolean): @NlsSafe String =
      Printer(width, startColumn, html).print(Command(indent, Mode.BREAK, chunk))

    /** The number of visible columns in the last line of [html]. */
    @JvmStatic
    fun lastLineWidth(html: String): Int = visibleWidth(html.substringAfterLast('\n'), true)

    private fun interleave(items: List<HtmlChunk>, separator: HtmlChunk): List<HtmlChunk> {
      val result = ArrayList<HtmlChunk>(items.size * 2)
      for (item in items) {
        if (result.isNotEmpty()) {
          result.add(separator)
        }
        result.add(item)
      }
      return result
    }

    private fun visibleWidth(text: String, html: Boolean): Int =
      if (html) StringUtil.unescapeXmlEntities(StringUtil.stripHtml(text, false)).length else text.length
  }

  private enum class Mode { FLAT, BREAK }

  private class Command(val indent: Int, val mode: Mode, val node: HtmlChunk)

  /** A Wadler-style printer, with the fill of Prettier. */
  private class Printer(private val width: Int, startColumn: Int, private val html: Boolean) {
    private val output = StringBuilder()
    private var column = startColumn

    fun print(root: Command): String {
      val stack = ArrayList<Command>()
      stack.add(root)
      while (stack.isNotEmpty()) {
        val command = stack.removeAt(stack.lastIndex)
        val indent = command.indent
        val mode = command.mode
        when (val node = command.node) {
          is Concat -> pushParts(stack, command, node.parts)
          is Group -> {
            val flat = Command(indent, Mode.FLAT, node.content)
            stack.add(if (mode == Mode.FLAT || column >= width || fits(flat, stack)) flat else Command(indent, Mode.BREAK, node.content))
          }
          is Indent -> stack.add(Command(indent + INDENT_SIZE, mode, node.content))
          is Line -> when (mode) {
            Mode.FLAT -> text(node.flat)
            Mode.BREAK -> {
              text(node.beforeBreak)
              newLine(indent)
              text(node.afterBreak)
            }
          }
          is Fill -> when (mode) {
            Mode.FLAT -> pushParts(stack, command, node.parts)
            Mode.BREAK -> fill(stack, indent, node.parts)
          }
          else -> text(node)
        }
      }
      return output.toString()
    }

    /**
     * Prints the first item of a broken [Fill], and decides if the separator after it breaks.
     * The separator stays flat when the next item also fits on the line.
     */
    private fun fill(stack: MutableList<Command>, indent: Int, parts: List<HtmlChunk>) {
      val content = parts.firstOrNull() ?: return
      val contentFits = fits(Command(indent, Mode.FLAT, content), restAfter(parts, 0, indent, stack))
      val contentCommand = Command(indent, if (contentFits) Mode.FLAT else Mode.BREAK, content)
      if (parts.size < 3) {
        if (parts.size == 2) {
          stack.add(Command(indent, if (contentFits) Mode.FLAT else Mode.BREAK, parts[1]))
        }
        stack.add(contentCommand)
        return
      }
      val separator = parts[1]
      val pair = Command(indent, Mode.FLAT, Concat(listOf(content, separator, parts[2])))
      val pairFits = fits(pair, restAfter(parts, 2, indent, stack))
      stack.add(Command(indent, Mode.BREAK, Fill(parts.subList(2, parts.size))))
      stack.add(Command(indent, if (pairFits) Mode.FLAT else Mode.BREAK, separator))
      stack.add(contentCommand)
    }

    /** The commands that follow the item at [index] of a [Fill]: the separator after it, or else the rest of the [stack]. */
    private fun restAfter(parts: List<HtmlChunk>, index: Int, indent: Int, stack: List<Command>): List<Command> =
      if (index + 1 < parts.size) listOf(Command(indent, Mode.BREAK, parts[index + 1])) else stack

    /**
     * Tells if [next] fits flat in the rest of the line.
     * The check goes on into [rest], up to the first line break that [rest] can make, and counts the text before that break.
     */
    private fun fits(next: Command, rest: List<Command>): Boolean {
      var remaining = width - column
      val commands = ArrayList<Command>()
      commands.add(next)
      var restIndex = rest.size
      while (remaining >= 0) {
        if (commands.isEmpty()) {
          if (restIndex == 0) return true
          commands.add(rest[--restIndex])
          continue
        }
        val command = commands.removeAt(commands.lastIndex)
        when (val node = command.node) {
          is Concat -> pushParts(commands, command, node.parts)
          is Group -> commands.add(Command(command.indent, command.mode, node.content))
          is Indent -> commands.add(Command(command.indent, command.mode, node.content))
          is Line -> when (command.mode) {
            Mode.FLAT -> remaining -= widthOf(node.flat)
            Mode.BREAK -> return remaining >= widthOf(node.beforeBreak)
          }
          is Fill -> pushParts(commands, command, node.parts)
          else -> remaining -= widthOf(node)
        }
      }
      return false
    }

    private fun pushParts(stack: MutableList<Command>, command: Command, parts: List<HtmlChunk>) {
      for (part in parts.asReversed()) {
        stack.add(Command(command.indent, command.mode, part))
      }
    }

    private fun text(chunk: HtmlChunk) {
      chunk.appendTo(output)
      column += widthOf(chunk)
    }

    private fun newLine(indent: Int) {
      output.append('\n')
      repeat(indent) { output.append(' ') }
      column = indent
    }

    private fun widthOf(chunk: HtmlChunk): Int = visibleWidth(chunk.toString(), html)
  }
}
