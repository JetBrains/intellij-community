// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

/** The role of a [PyTestDirective] in a test text. */
enum class PyTestDirectiveKind {
  /** Checks one value at a marked position or on a line. */
  ASSERTION,

  /** Changes how an assertion compares its value. It is a word inside the payload of an assertion. */
  MODIFIER,
}

/**
 * One word of the assertion mini-language of `PyCodeInsightTestCase`.
 *
 * The test engine computes the actual value of each assertion. This class only describes the syntax.
 * The texts are English DevKit texts for the developers of the IntelliJ Platform. This library has no message bundle,
 * so they are not in a `*.properties` file. The same is true for the messages of the parser.
 *
 * @property payloadGrammar the text that can follow the name, in a short informal notation.
 * @property isSeverity true for a highlight severity. The engine compares such an assertion with the highlight description.
 * @property values the words that the engine can compute for this directive. An empty list means any text.
 */
class PyTestDirective internal constructor(
  val name: String,
  val kind: PyTestDirectiveKind,
  val payloadGrammar: String,
  val description: String,
  val isSeverity: Boolean = false,
  val values: List<String> = emptyList(),
  private val payloadCheck: (String) -> String? = { null },
) {
  /**
   * Returns a problem description when [payload] does not match [payloadGrammar], or null when it matches.
   *
   * A payload of one word must be one of [values]. A payload of more words is a message of the engine, so it is not checked.
   */
  fun checkPayload(payload: String): String? {
    payloadCheck(payload)?.let { return it }
    if (values.isEmpty() || payload.isEmpty() || payload.any { it.isWhitespace() } || payload in values) return null
    return "Unknown value '$payload' after $name. Expected one of: ${values.joinToString(", ")}"
  }

  override fun toString(): String = name
}

/** The registry of all [PyTestDirective]s. A new directive is one entry here and one computation in the engine. */
object PyTestDirectives {
  private val entries = mutableListOf<PyTestDirective>()

  private fun directive(
    name: String,
    kind: PyTestDirectiveKind,
    payloadGrammar: String,
    description: String,
    isSeverity: Boolean = false,
    values: List<String> = emptyList(),
    payloadCheck: (String) -> String? = { null },
  ): PyTestDirective = PyTestDirective(name, kind, payloadGrammar, description, isSeverity, values, payloadCheck).also { entries += it }

  /** The names of `PyVariance`. The engine writes them for a variance directive. */
  private val varianceNames = listOf("COVARIANT", "CONTRAVARIANT", "INVARIANT", "BIVARIANT", "INFER_VARIANCE")

  private fun severity(name: String): PyTestDirective =
    directive(name, PyTestDirectiveKind.ASSERTION, "[<message> | TOOLTIP <fragment>]",
              "A highlight of severity $name with this description at the marked position", isSeverity = true)

  val TYPE: PyTestDirective = directive(
    "TYPE", PyTestDirectiveKind.ASSERTION, "<type>",
    "The declared or inferred type of the marked element, in both the code analysis and the user-initiated context",
  )

  val EXPECTED_TYPE: PyTestDirective = directive(
    "EXPECTED_TYPE", PyTestDirectiveKind.ASSERTION, "<type>",
    "The type that the location of the marked expression expects",
  )

  val IS_BUILTIN: PyTestDirective = directive(
    "IS_BUILTIN", PyTestDirectiveKind.ASSERTION, "[FALSE]",
    "The marked element is a builtin. FALSE states that it is not a builtin",
    values = listOf("FALSE"),
  ) { payload -> if (payload.isEmpty() || payload == "FALSE") null else "Expected nothing or FALSE after IS_BUILTIN" }

  val EXPECTED_VARIANCE: PyTestDirective = directive(
    "EXPECTED_VARIANCE", PyTestDirectiveKind.ASSERTION, "<variance>",
    "The variance that the location of the marked reference expects. NONE states that the location expects no variance",
    values = varianceNames + "NONE",
  )

  val INFERRED_VARIANCE: PyTestDirective = directive(
    "INFERRED_VARIANCE", PyTestDirectiveKind.ASSERTION, "<variance>",
    "The declared or inferred variance of the marked type parameter. Unknown states that the engine found no variance",
    values = varianceNames + "Unknown",
  )

  val ISSUES: PyTestDirective = directive(
    "ISSUES", PyTestDirectiveKind.ASSERTION, "*",
    "Any number of highlights of any severity on the line. Other assertions on the line take their highlights first",
    values = listOf("*"),
  ) { payload -> if (payload == "*") null else "Expected * after ISSUES" }

  val CARET: PyTestDirective = directive(
    "CARET", PyTestDirectiveKind.ASSERTION, "",
    "Puts the caret at the marked position. It checks nothing",
  ) { payload -> if (payload.isEmpty()) null else "Expected nothing after CARET" }

  init {
    for (name in listOf("ERROR", "WARNING", "WEAK_WARNING", "INFO", "INFORMATION", "SERVER_PROBLEM", "TEXT_ATTRIBUTES")) {
      severity(name)
    }
  }

  val FIXME: PyTestDirective = directive(
    "FIXME", PyTestDirectiveKind.MODIFIER, "[<value after the fix>]",
    "The value before FIXME is wrong today. The test passes with it and fails when a fix changes it. " +
    "Without text after FIXME, the assertion must disappear after the fix",
  )

  /**
   * Switches a severity assertion to the tooltip of the highlight.
   *
   * The expected text needs only to be part of the tooltip. Use `\n` to separate the lines. The engine checks each line separately.
   * When the text contains a closing HTML tag (`</` or `/>`), the engine compares it with the HTML of the tooltip.
   * Otherwise, it removes the HTML tags from the tooltip first.
   */
  val TOOLTIP: PyTestDirective = directive(
    "TOOLTIP", PyTestDirectiveKind.MODIFIER, "<fragment>[\\n<fragment>...]",
    "After a severity: the tooltip of the highlight contains each fragment",
  )

  /** All directives, in the order of declaration. */
  val all: List<PyTestDirective> = entries.toList()

  /** The names of the severity directives. They are the names of the default highlight severities with `_` in place of a space, as in the other directive names. */
  val severityNames: Set<String> = all.filter { it.isSeverity }.mapTo(LinkedHashSet()) { it.name }

  private val byName: Map<String, PyTestDirective> = all.associateBy { it.name }

  /** Finds a directive by its exact name. Directive names are case-sensitive. */
  fun find(name: String): PyTestDirective? = byName[name]

  /** Finds an assertion directive by its exact name. */
  fun findAssertion(name: String): PyTestDirective? = find(name)?.takeIf { it.kind == PyTestDirectiveKind.ASSERTION }

  /**
   * Finds the assertion directive that [name] probably means, when [name] is not a directive itself.
   *
   * A near miss uses `_` in place of `-`, or the reverse.
   */
  fun findNearMiss(name: String): PyTestDirective? {
    if (find(name) != null) return null
    return findAssertion(name.replace('_', '-')) ?: findAssertion(name.replace('-', '_'))
  }
}
