// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands.codeTyping

import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.Keys
import com.intellij.tools.ide.performanceTesting.commands.codeTyping.CodeTypingCommandGenerator.generateCommands
import com.intellij.tools.ide.performanceTesting.commands.codeTyping.CodeTypingCommandGenerator.generateReverseCommands
import com.intellij.tools.ide.performanceTesting.commands.codeTyping.CodeTypingCommandGenerator.preparePlan
import com.intellij.tools.ide.performanceTesting.commands.delay
import com.intellij.tools.ide.performanceTesting.commands.delayType
import com.intellij.tools.ide.performanceTesting.commands.doComplete
import com.intellij.tools.ide.performanceTesting.commands.doCompleteWarmup
import com.intellij.tools.ide.performanceTesting.commands.pressKey
import com.intellij.tools.ide.performanceTesting.commands.startNewSpan
import com.intellij.tools.ide.performanceTesting.commands.stopSpan
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished

/**
 * [CodeTypingCommandGenerator] generates a sequence of [CommandChain] commands for typing a specific input string with completion and
 * highlighting. The input string contains special syntax for invoking completion, explicit identifier shortcuts for completion, and
 * skipping automatically inserted braces. The generator is also able to generate a *reverse* sequence of commands for undoing the changes
 * with [generateReverseCommands].
 *
 * With [preparePlan], the generator first creates a plan from the input string, which is data-driven and can be inspected in the debugger.
 * The plan can then be passed to [generateCommands] to generate the actual commands into the given [CommandChain]. The same plan can be
 * reused multiple times, for example, to generate commands for multiple test iterations.
 *
 * The plan is created according to the following rules:
 *
 * - Identifiers marked with `~` become [CodeTypingPlan.CompletionToken]s with shortcuts for code completion.
 *   Use `~[shortcut]identifier` for explicit shortcuts, or `~identifier` to generate one automatically.
 * - Newline characters (`\n`) become [CodeTypingPlan.NewLineToken]s.
 * - Down arrow tokens (`↓`) become [CodeTypingPlan.SkipLineToken]s. They should be used to skip automatically inserted braces and
 *   parentheses closing multi-line blocks. If the brace or parenthesis is on the same line, the IDE is usually able to skip it
 *   intelligently when it's typed. But this doesn't work for blocks where the closing brace/parenthesis is on the next line.
 * - All other text becomes [CodeTypingPlan.TextToken]s with trimmed indent.
 *
 * The command generator currently **does not support the completion of identifiers with *one candidate***. In this case, explicit
 * completion automatically inserts the full identifier. Because of this, the performance test cannot correctly delete the typed identifier
 * to replace it with the full one (which in turn is a workaround for not being able to pick the candidate from the completion popup).
 * Unfortunately, on the performance test side, we don't have enough information about the completion to make a decision.
 *
 * #### Example input
 *
 * Here is a possible input to prepare a plan:
 *
 * ```
 * fun transformString(input: ~[Str]String) = run {
 *     val normalizedInput = input.trim().~lowercase().~replace(" ", "-")
 *     val shortenedInput = if (normalizedInput.~[l]length > 10) normalizedInput.~substring(0, 10) else normalizedInput
 *
 *     ~shortenedInput.~replaceFirstChar { firstChar ->
 *         if (~firstChar.~isLowerCase()) ~firstChar.~titlecase()
 *         else ~firstChar.~[toS]toString()
 *     ↓
 * ↓
 * ```
 *
 * The whole function will be typed out.
 *
 * `~[Str]String` means that the test will type `Str`, then wait for completion, and finish with typing `String`. `~lowercase` without the
 * brackets means that the test will choose its own shortcut to type and invoke completion.

 */
object CodeTypingCommandGenerator {
  const val CODE_TYPING_SPAN_NAME: String = "codeTyping"

  private const val CODE_TYPING_WARMUP_SPAN_NAME = "${CODE_TYPING_SPAN_NAME}_warmup"

  /**
   * A lower delay would be ideal, but at some point the code typing becomes unstable. For example, quote matching generating additional
   * quotes.
   */
  private const val TYPING_DELAY_MS: Int = 50

  /**
   * @see CodeTypingCommandGenerator
   */
  fun preparePlan(input: String): CodeTypingPlan = CodeTypingPlanGenerator.generatePlan(input)

  /**
   * @see CodeTypingCommandGenerator
   */
  fun <T : CommandChain> generateCommands(commands: T, plan: CodeTypingPlan, isWarmup: Boolean): T {
    val spanName = if (isWarmup) CODE_TYPING_WARMUP_SPAN_NAME else CODE_TYPING_SPAN_NAME

    commands.startNewSpan(spanName)

    plan.tokens.forEach { token ->
      generateTokenCommand(commands, token, isWarmup)
    }

    // As the code typing has finished, let's wait for highlighting one more time in case we didn't finish the code with a new line.
    commands.waitForCodeAnalysisFinished()

    commands.stopSpan(spanName)

    return commands
  }

  private fun <T : CommandChain> generateTokenCommand(
    commands: T,
    token: CodeTypingPlan.Token,
    isWarmup: Boolean,
  ) {
    when (token) {
      is CodeTypingPlan.TextToken -> {
        commands.delayType(TYPING_DELAY_MS, token.text)
      }

      CodeTypingPlan.NewLineToken -> {
        commands.pressKey(Keys.ENTER)

        // In a real-world scenario, the user's typing rhythm isn't as steady as the typing driven by performance tests. This
        // gives natural pauses to update the highlighting. To simulate this, we insert highlighting breaks after each new line.
        commands.waitForCodeAnalysisFinished()
      }

      CodeTypingPlan.SkipLineToken -> {
        commands.pressKey(Keys.ARROW_DOWN)
      }

      is CodeTypingPlan.CompletionToken -> {
        // It would be nice to pick the item from the list, but this isn't so easy with the current performance testing setup.
        // `CompletionCommand` does not support this logic. So for now, we're invoking completion, removing the shortcut, and
        // typing out the full name.
        commands.delayType(TYPING_DELAY_MS, token.shortcut)

        if (isWarmup) {
          commands.doCompleteWarmup()
        }
        else {
          commands.doComplete()
        }

        // A short delay is necessary for stability. Nonetheless, asserting that the completion had results is currently not
        // possible because it is still slightly unstable and might fail from time to time. For example, the completion might
        // occasionally fail to show any candidates. Since we are not testing a single completion case but a complex mix of typing,
        // completion, and code analysis, it's unwise to fail the whole test because of a small instability.
        commands.delay(200)
        commands.pressKey(Keys.ESCAPE)

        repeat(token.shortcut.length) {
          commands.pressKey(Keys.BACKSPACE)
        }

        // Shorter delays have a higher risk of swapped letters because the coroutines on the command side are spawned all at
        // once.
        commands.delayType(10, token.fullIdentifier)
      }
    }
  }

  /**
   * Generates a sequence of commands to undo the changes made with [generateCommands].
   */
  fun <T : CommandChain> generateReverseCommands(commands: T, plan: CodeTypingPlan): T {
    // We have one line at the start where we type, and then as many lines as new line *and* skip line tokens.
    //
    // For the skip-line token, consider an example. We type: `run {\n}`. This becomes:
    //   run {
    //     <caret>
    //   }
    //
    // So by adding one new line, we have two lines that need to be deleted later. The equivalent code typing input will be:
    //   run {
    //
    //   ↓
    val lines = 1 + plan.tokens.count { it is CodeTypingPlan.NewLineToken || it is CodeTypingPlan.SkipLineToken }
    repeat(lines) {
      commands.pressKey(Keys.DELETE_LINE)
      commands.pressKey(Keys.ARROW_UP)
    }

    // This ENTER brings the test into a neutral position, since we've deleted one more line than we've added.
    commands.pressKey(Keys.ENTER)

    return commands
  }
}

/**
 * @see CodeTypingCommandGenerator
 */
class CodeTypingPlan(val tokens: List<Token>) {
  sealed class Token

  data class TextToken(val text: String) : Token()
  data object NewLineToken : Token()
  data object SkipLineToken : Token()
  data class CompletionToken(val shortcut: String, val fullIdentifier: String) : Token()
}

private object CodeTypingPlanGenerator {
  /**
   * Parses the input string and generates a plan according to the documented semantics of [CodeTypingCommandGenerator].
   */
  fun generatePlan(input: String): CodeTypingPlan {
    val tokens = parseInput(input)
    val simplifiedTokens = simplifyTokens(tokens)
    return CodeTypingPlan(simplifiedTokens)
  }

  private fun parseInput(input: String): MutableList<CodeTypingPlan.Token> {
    val tokens = mutableListOf<CodeTypingPlan.Token>()
    val rawBuffer = StringBuilder()

    fun flushRawBuffer() {
      if (rawBuffer.isNotEmpty()) {
        val text = rawBuffer.toString()

        // We cannot just trim the raw input, because whitespace between certain parseInput might be significant. For example,
        // `else ~foo` would turn into `elsefoo` if `else ` gets trimmed. However, it's possible to trim the initial indent of a new
        // line.
        val trimmedText = if (tokens.lastOrNull() == CodeTypingPlan.NewLineToken) text.trimStart() else text

        if (trimmedText.isNotEmpty()) {
          tokens.add(CodeTypingPlan.TextToken(trimmedText))
        }
        rawBuffer.clear()
      }
    }

    var i = 0
    while (i < input.length) {
      when (input[i]) {
        '~' -> {
          flushRawBuffer()
          i += 1 // skip the `~`

          val (explicitShortcut, newIndex) = parseExplicitShortcut(input, i)
          i = newIndex

          val (identifier, newIndex2) = parseIdentifier(input, i)
          i = newIndex2

          val shortcut = explicitShortcut ?: generateShortcut(identifier)

          tokens.add(CodeTypingPlan.CompletionToken(shortcut, identifier))
        }

        '\n' -> {
          flushRawBuffer()
          tokens.add(CodeTypingPlan.NewLineToken)
          i += 1
        }

        '↓' -> {
          flushRawBuffer()
          tokens.add(CodeTypingPlan.SkipLineToken)
          i += 1
        }

        else -> {
          rawBuffer.append(input[i])
          i += 1
        }
      }
    }

    flushRawBuffer()
    return tokens
  }

  /**
   * Parses an explicit shortcut in brackets (e.g., `[shortcut]`).
   *
   * @return The shortcut and updated index, or `null` and unchanged index if no brackets found.
   * @throws IllegalStateException If opening bracket exists but closing bracket is missing.
   */
  private fun parseExplicitShortcut(input: String, startIndex: Int): Pair<String?, Int> {
    var i = startIndex

    return if (i < input.length && input[i] == '[') {
      i += 1 // skip past the `[`

      val (shortcut, newIndex) = parseIdentifier(input, i)
      i = newIndex

      if (i < input.length && input[i] == ']') {
        i += 1 // skip past the `]`
      }
      else {
        error("Missing closing `]` in explicit shortcut for completion command!")
      }

      shortcut to i
    }
    else {
      null to i
    }
  }

  /**
   * Generates a shortcut from an identifier using camelCase heuristics.
   *
   * Extracts the first camelCase word (up to the next uppercase letter).
   * Returns the first word if it's ≤ 5 characters, otherwise returns the first 5 characters of the identifier.
   *
   * Examples: `transformer` → `trans`, `withFirEntry` → `with`, `check` → `check`
   */
  private fun generateShortcut(identifier: String): String {
    if (identifier.isEmpty()) return ""

    var firstWordEnd = 1
    while (firstWordEnd < identifier.length && !identifier[firstWordEnd].isUpperCase()) {
      firstWordEnd += 1
    }

    val firstWord = identifier.substring(0, firstWordEnd)
    return firstWord.take(5)
  }

  private fun parseIdentifier(input: String, startIndex: Int): Pair<String, Int> {
    var i = startIndex
    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) {
      i += 1
    }
    val identifier = input.substring(startIndex, i)
    return identifier to i
  }

  private fun simplifyTokens(tokens: List<CodeTypingPlan.Token>): List<CodeTypingPlan.Token> {
    return buildList {
      tokens.forEachIndexed { index, token ->
        // When we have a combination 'New Line' + 'Skip Line', the new line will effectively be empty because we immediately skip
        // it. So we can remove the 'New Line' token and just skip the following line.
        //
        // Example:
        //   run {
        //     foo()
        //   ↓
        //
        // Without this simplification, we would generate:
        //
        // TextToken("run {"), NewLineToken, TextToken("foo()"), NewLineToken, SkipLineToken
        //
        // This would result in the following typed code:
        //   run {
        //     foo()
        //
        //   }<caret>
        //
        // It would be possible to just place `↓` on the same line, but it would make the input code harder to read:
        //   run {
        //     foo()↓
        //
        // When `↓` is on its own line, it's in place of the automatically inserted closing brace/parenthesis.
        val nextToken = tokens.getOrNull(index + 1)
        if (token == CodeTypingPlan.NewLineToken && nextToken == CodeTypingPlan.SkipLineToken) {
          return@forEachIndexed
        }

        add(token)
      }
    }
  }
}
